package com.dating.server.match.recommend;

import com.dating.proto.user.DhCandidate;
import com.dating.proto.user.NearbyUser;
import com.dating.proto.user.UserProfile;
import com.dating.server.match.client.UserServiceClient;
import com.dating.server.match.constant.CacheKeys;
import com.dating.server.match.constant.Constants;
import com.dating.server.match.manager.LikeRecordManager;
import com.dating.server.match.manager.SwipeHistoryManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * D1 日更队列生成器（PRD 4.2）
 *
 * 每日凌晨为"昨天有划卡行为"的用户生成 240 张推荐队列：
 *   1. PreferenceBuilder 偏好建模
 *   2. CandidateRecaller 两池独立召回
 *   3. Ranker 池内打分
 *   4. 按比例 merge + RPUSH 到 Redis LIST
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class D1Generator {

    private final PreferenceBuilder preferenceBuilder;
    private final CandidateRecaller candidateRecaller;
    private final Ranker ranker;
    private final UserServiceClient userServiceClient;
    private final SwipeHistoryManager swipeHistoryManager;
    private final LikeRecordManager likeRecordManager;
    private final StringRedisTemplate stringRedisTemplate;

    /** 队列固定容量 240 */
    private static final int QUEUE_SIZE = 240;
    /** LIST TTL 7 天 */
    private static final long QUEUE_TTL_DAYS = 7;

    @Value("${match.d1.bh_ratio:0.40}")
    private double bhRatio;

    @Value("${match.d1.preference_enabled:true}")
    private boolean preferenceEnabled;

    @Value("${match.d1.preference_offset:0.20}")
    private double preferenceOffset;

    /**
     * 为用户生成 D1 队列
     *
     * @return true=成功生成, false=跳过（无划卡历史）
     */
    public boolean generateForUser(Long userId) {
        // 1. 前置条件：用户昨天有划卡行为
        Instant yesterdayStart = Instant.now().minusSeconds(86400);
        boolean hasRecentSwipe = swipeHistoryManager.hasSwipeSince(userId, yesterdayStart);
        if (!hasRecentSwipe) {
            log.debug("D1 跳过: 用户昨天无划卡, userId={}", userId);
            return false;
        }

        // 2. 获取用户资料
        UserProfile self = userServiceClient.getProfile(userId);
        if (self == null) {
            log.warn("D1 跳过: 获取用户资料失败, userId={}", userId);
            return false;
        }

        int userGender = self.getGender();
        int targetGender = oppositeGender(userGender);
        int userAge = self.getAge();
        int userBeauty = self.getBeautyScore();
        String userRace = self.getRace();

        // 3. 偏好建模
        PreferenceProfile pref = preferenceBuilder.build(userId, self);

        // 4. 获取已 swipe 排除列表
        Set<Long> excludeUserIds = getSwipedTargets(userId);

        // 5. 两池召回
        List<DhCandidate> dhPool = candidateRecaller.recallDh(
                targetGender, 0, 999, 0, 999,
                Collections.emptyList(), excludeUserIds);
        List<NearbyUser> bhPool = candidateRecaller.recallBhD1(
                userId, targetGender, excludeUserIds);

        // service 层进一步排除同性别
        bhPool = bhPool.stream()
                .filter(u -> u.getGender() == targetGender)
                .collect(Collectors.toList());

        log.info("D1 召回: userId={}, DH={}张, BH={}张", userId, dhPool.size(), bhPool.size());

        // 6. 池内打分排序
        var rankedDh = ranker.rankDh(dhPool, pref, userAge, userBeauty);

        // 6b. 构建 mutualCache：检测 BH 池中哪些候选人已右划过当前用户
        Map<String, Boolean> mutualCache = buildMutualCache(userId, bhPool);
        var rankedBh = ranker.rankBh(bhPool, pref, userAge, userBeauty,
                excludeUserIds, mutualCache);

        // 7. 按比例 merge（PRD 4.2.5）
        double finalBhRatio = calcFinalBhRatio(pref);
        List<CardEntry> merged = mergePools(rankedBh, rankedDh, finalBhRatio);

        if (merged.isEmpty()) {
            log.warn("D1 merge 结果为空, userId={}", userId);
            return false;
        }

        // 8. 覆盖式写入 Redis LIST（DEL + RPUSH）
        String feedKey = CacheKeys.feed(userId);
        stringRedisTemplate.delete(feedKey);
        for (CardEntry card : merged) {
            stringRedisTemplate.opsForList().rightPush(
                    feedKey, card.userId + ":" + card.userType);
        }
        stringRedisTemplate.expire(feedKey, QUEUE_TTL_DAYS, TimeUnit.DAYS);

        log.info("D1 队列生成完成: userId={}, 推送={}张, DH={}/BH={}, bhRatio={}",
                userId, merged.size(),
                merged.stream().filter(c -> c.userType == Constants.USER_TYPE_DH).count(),
                merged.stream().filter(c -> c.userType == Constants.USER_TYPE_BH).count(),
                String.format("%.2f", finalBhRatio));
        return true;
    }

    /**
     * 计算最终 BH 比例（PRD 4.2.5）
     *
     * final_bh_ratio = clamp(
     *     L1 运营基础比例 (match.d1.bh_ratio)
     *     + L2 个性化偏移 (基于用户 30 天右划 dh_bh_ratio),
     *   0, 1)
     */
    private double calcFinalBhRatio(PreferenceProfile pref) {
        double ratio = bhRatio; // L1

        if (preferenceEnabled && pref.hasSufficientSamples()) {
            // L2: 偏移量 = (0.5 - dh_bh_ratio) * offset * 2, clamp 到 [-offset, +offset]
            double offset = (0.5 - pref.dhBhRatio()) * preferenceOffset * 2;
            offset = Math.max(-preferenceOffset, Math.min(preferenceOffset, offset));
            ratio += offset;
        }

        return Math.max(0, Math.min(1, ratio));
    }

    /**
     * 按比例 merge（PRD 4.2.5 算法）
     */
    private List<CardEntry> mergePools(List<Ranker.ScoredBh> bhPool,
                                        List<Ranker.ScoredDh> dhPool,
                                        double finalBhRatio) {
        int targetBh = (int) Math.round(QUEUE_SIZE * finalBhRatio);
        int targetDh = QUEUE_SIZE - targetBh;

        int actualBh = Math.min(targetBh, bhPool.size());
        int shortage = targetBh - actualBh;
        int actualDh = Math.min(targetDh + shortage, dhPool.size());

        List<CardEntry> result = new ArrayList<>(actualBh + actualDh);

        int interleaveStep = finalBhRatio > 0 ? (int) Math.round(1.0 / finalBhRatio) : Integer.MAX_VALUE;
        int bhIdx = 0, dhIdx = 0;

        while (bhIdx < actualBh && dhIdx < actualDh) {
            // 1 张 BH
            result.add(new CardEntry(bhPool.get(bhIdx++).candidate().getUserId(), Constants.USER_TYPE_BH));
            // n-1 张 DH
            for (int i = 1; i < interleaveStep && dhIdx < actualDh; i++) {
                result.add(new CardEntry(dhPool.get(dhIdx++).candidate().getUserId(), Constants.USER_TYPE_DH));
            }
        }

        // 剩余 BH
        while (bhIdx < actualBh) {
            result.add(new CardEntry(bhPool.get(bhIdx++).candidate().getUserId(), Constants.USER_TYPE_BH));
        }
        // 剩余 DH
        while (dhIdx < actualDh) {
            result.add(new CardEntry(dhPool.get(dhIdx++).candidate().getUserId(), Constants.USER_TYPE_DH));
        }

        return result;
    }

    /**
     * 构建 mutualCache：检测 BH 池中哪些候选人已右划过当前用户
     *
     * 结果格式: Map<"min(user,candidate):max(user,candidate)", true>
     * Ranker 中读取此缓存为 mutual_like_bonus 加分
     */
    private Map<String, Boolean> buildMutualCache(Long currentUserId, List<NearbyUser> candidates) {
        if (candidates == null || candidates.isEmpty()) return Collections.emptyMap();

        List<Long> candidateIds = candidates.stream()
                .map(NearbyUser::getUserId)
                .collect(Collectors.toList());

        List<Long> mutualIds = likeRecordManager.listWhoLikedMeByCandidates(currentUserId, candidateIds);
        if (mutualIds.isEmpty()) return Collections.emptyMap();

        Map<String, Boolean> cache = new HashMap<>(mutualIds.size());
        for (Long candidateId : mutualIds) {
            cache.put(String.valueOf(candidateId), true);
        }
        return cache;
    }

    private Set<Long> getSwipedTargets(Long userId) {
        try {
            List<Long> ids = swipeHistoryManager.listSwipedUserIds(userId);
            return ids != null ? new HashSet<>(ids) : Collections.emptySet();
        } catch (Exception e) {
            log.warn("查询已 swipe 列表失败, userId={}", userId, e);
            return Collections.emptySet();
        }
    }

    private int oppositeGender(int gender) {
        return gender == 1 ? 2 : (gender == 2 ? 1 : 0);
    }

    record CardEntry(long userId, int userType) {}
}
