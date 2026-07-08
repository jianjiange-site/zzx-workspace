package com.dating.server.match.service;

import com.dating.proto.user.DhCandidate;
import com.dating.proto.user.NearbyUser;
import com.dating.server.match.client.UserServiceClient;
import com.dating.server.match.constant.CacheKeys;
import com.dating.server.match.constant.Constants;
import com.dating.server.match.manager.SwipeHistoryManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * D0 冷启动队列 —— 实时双池召回 + 排序 + 比例 merge
 *
 * PRD 4.1:
 *   - DH 池：调 user-service.listDhCandidates 渐进扩范围 L0~L3（必须凑 240）
 *   - BH 池：调 user-service.nearbyUsers 严格条件单次（不够就 DH 补齐）
 *   - 池内字典序排序、按 bh_ratio merge 后 RPUSH 到 Redis LIST
 *
 * 与 D1 共享同一套召回流程，仅偏好来源不同：
 *   D0 = 用户自身画像作为 prior, D1 = 30 天右划画像分布
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColdStartService {

    private final StringRedisTemplate stringRedisTemplate;
    private final SwipeHistoryManager swipeHistoryManager;
    private final UserServiceClient userServiceClient;

    /** 队列固定容量 240 张 */
    private static final int QUEUE_SIZE = 240;
    /** LIST TTL 7 天 */
    private static final long QUEUE_TTL_DAYS = 7;

    // ── BH 池默认阈值（Nacos 可覆盖） ──
    @Value("${match.cold_start.bh.radius_km:100}")
    private int bhRadiusKm;
    @Value("${match.cold_start.bh.age_window:5}")
    private int bhAgeWindow;
    @Value("${match.cold_start.bh.beauty_window:15}")
    private int bhBeautyWindow;
    @Value("${match.cold_start.bh.active_days:7}")
    private int bhActiveDays;

    // ── DH 渐进层级阈值（Nacos 可覆盖） ──
    @Value("${match.cold_start.levels.l0.age_window:5}")
    private int l0AgeWindow;
    @Value("${match.cold_start.levels.l0.beauty_window:15}")
    private int l0BeautyWindow;
    @Value("${match.cold_start.levels.l1.age_window:5}")
    private int l1AgeWindow;
    @Value("${match.cold_start.levels.l1.beauty_window:15}")
    private int l1BeautyWindow;
    @Value("${match.cold_start.levels.l2.age_window:10}")
    private int l2AgeWindow;
    @Value("${match.cold_start.levels.l2.beauty_window:25}")
    private int l2BeautyWindow;

    // ── BH 比例 ──
    @Value("${match.cold_start.bh_ratio:0.20}")
    private double bhRatio;

    // ── 新人窗口 ──
    @Value("${match.score.new_bh_window_days:3}")
    private int newBhWindowDays;

    /** 新 BH 阈值（毫秒） */
    private long newBhThresholdMs() {
        return (long) newBhWindowDays * 24 * 3600 * 1000;
    }

    public void buildAndPush(Long userId) {
        // 1. 获取用户性别（用于异性过滤）
        var selfProfile = userServiceClient.getProfile(userId);
        if (selfProfile == null) {
            log.warn("获取用户资料失败, 跳过队列重建: userId={}", userId);
            return;
        }
        int userGender = selfProfile.getGender();
        int targetGender = oppositeGender(userGender);
        int userAge = selfProfile.getAge();
        int userBeauty = selfProfile.getBeautyScore();
        String userRace = selfProfile.getRace();

        // 2. 获取已 swipe 过的 target 列表
        Set<Long> swipedTargets = getSwipedTargets(userId);

        // 3. 双池召回
        List<DhCandidate> dhPool = recallDhPool(targetGender, userAge, userBeauty, userRace, swipedTargets);
        List<NearbyUser> bhPool = recallBhPool(userId, targetGender, userAge, userBeauty, userRace, swipedTargets);

        log.info("D0 召回: userId={}, DH={}张, BH={}张", userId, dhPool.size(), bhPool.size());

        // 4. 池内排序
        sortDhPool(dhPool, userAge, userBeauty, userRace);
        sortBhPool(bhPool, userAge, userBeauty, userRace);

        // 5. 按比例 merge
        List<CardEntry> merged = mergePools(bhPool, dhPool);

        if (merged.isEmpty()) {
            log.warn("D0 merge 结果为空, 跳过队列重建: userId={}", userId);
            return;
        }

        // 6. RPUSH 到 Redis LIST
        String feedKey = CacheKeys.feed(userId);
        // D0 使用 RPUSH 不带 DEL（允许追加），PRD 4.1
        for (CardEntry card : merged) {
            stringRedisTemplate.opsForList().rightPush(feedKey, card.userId + ":" + card.userType);
        }
        stringRedisTemplate.expire(feedKey, QUEUE_TTL_DAYS, TimeUnit.DAYS);

        log.info("D0 队列重建完成: userId={}, 推送={}张, DH={}/BH={}",
                userId, merged.size(),
                merged.stream().filter(c -> c.userType == Constants.USER_TYPE_DH).count(),
                merged.stream().filter(c -> c.userType == Constants.USER_TYPE_BH).count());
    }

    // ═══════════════════════════════════════════
    //  DH 池：渐进扩范围 L0~L3
    // ═══════════════════════════════════════════

    private List<DhCandidate> recallDhPool(int targetGender, int userAge, int userBeauty,
                                            String userRace, Set<Long> swipedTargets) {
        Set<Long> accumulated = new HashSet<>();
        List<DhCandidate> result = new ArrayList<>(QUEUE_SIZE);
        List<Long> excludeList = new ArrayList<>(swipedTargets);

        // L0: 同人种 + ±5年龄 + ±15颜值
        if (result.size() < QUEUE_SIZE) {
            fillDhCandidates(result, accumulated, targetGender,
                    userAge - l0AgeWindow, userAge + l0AgeWindow,
                    userBeauty - l0BeautyWindow, userBeauty + l0BeautyWindow,
                    List.of(userRace), excludeList);
        }
        // L1: 不限人种 + ±5年龄 + ±15颜值
        if (result.size() < QUEUE_SIZE) {
            fillDhCandidates(result, accumulated, targetGender,
                    userAge - l1AgeWindow, userAge + l1AgeWindow,
                    userBeauty - l1BeautyWindow, userBeauty + l1BeautyWindow,
                    Collections.emptyList(), excludeList);
        }
        // L2: 不限人种 + ±10年龄 + ±25颜值
        if (result.size() < QUEUE_SIZE) {
            fillDhCandidates(result, accumulated, targetGender,
                    userAge - l2AgeWindow, userAge + l2AgeWindow,
                    userBeauty - l2BeautyWindow, userBeauty + l2BeautyWindow,
                    Collections.emptyList(), excludeList);
        }
        // L3: 兜底，只限异性 + 排除已 swipe
        if (result.size() < QUEUE_SIZE) {
            fillDhCandidates(result, accumulated, targetGender,
                    0, 999, 0, 999,
                    Collections.emptyList(), excludeList);
        }

        return result;
    }

    private void fillDhCandidates(List<DhCandidate> result, Set<Long> accumulated,
                                   int targetGender, int ageMin, int ageMax,
                                   int beautyMin, int beautyMax,
                                   List<String> races, List<Long> excludeList) {
        int remaining = QUEUE_SIZE - result.size();
        if (remaining <= 0) return;

        List<DhCandidate> candidates = userServiceClient.listDhCandidates(
                targetGender, ageMin, ageMax, beautyMin, beautyMax,
                races, excludeList, remaining);

        for (DhCandidate c : candidates) {
            if (accumulated.add(c.getUserId())) {
                result.add(c);
            }
        }
    }

    // ═══════════════════════════════════════════
    //  BH 池：严格条件单次调用
    // ═══════════════════════════════════════════

    private List<NearbyUser> recallBhPool(Long userId, int targetGender, int userAge,
                                           int userBeauty, String userRace,
                                           Set<Long> swipedTargets) {
        return userServiceClient.nearbyUsers(
                userId, targetGender,
                Math.max(0, userAge - bhAgeWindow), Math.min(99, userAge + bhAgeWindow),
                Math.max(0, userBeauty - bhBeautyWindow), Math.min(999, userBeauty + bhBeautyWindow),
                List.of(userRace), bhRadiusKm, bhActiveDays, QUEUE_SIZE,
                new ArrayList<>(swipedTargets));
    }

    // ═══════════════════════════════════════════
    //  池内排序
    // ═══════════════════════════════════════════

    /**
     * DH 池排序（3 级字典序）：同人种(desc) → 年龄差(asc) → 颜值(desc)
     */
    private void sortDhPool(List<DhCandidate> pool, int userAge, int userBeauty, String userRace) {
        pool.sort((a, b) -> {
            // 1. 同人种优先
            int raceCmp = -Boolean.compare(isSameRace(a.getRace(), userRace), isSameRace(b.getRace(), userRace));
            if (raceCmp != 0) return raceCmp;
            // 2. 年龄差小优先
            int ageCmp = Integer.compare(Math.abs(a.getAge() - userAge), Math.abs(b.getAge() - userAge));
            if (ageCmp != 0) return ageCmp;
            // 3. 颜值分高优先
            return -Integer.compare(a.getBeautyScore(), b.getBeautyScore());
        });
    }

    /**
     * BH 池排序（4 级字典序）：新BH(desc) → 同人种(desc) → 年龄差(asc) → 颜值(desc)
     */
    private void sortBhPool(List<NearbyUser> pool, int userAge, int userBeauty, String userRace) {
        long threshold = newBhThresholdMs();
        long now = Instant.now().toEpochMilli();

        pool.sort((a, b) -> {
            // 1. 新 BH 靠前（3天内注册）
            boolean aNew = (now - a.getCreatedAt()) <= threshold;
            boolean bNew = (now - b.getCreatedAt()) <= threshold;
            int newCmp = -Boolean.compare(aNew, bNew);
            if (newCmp != 0) return newCmp;
            // 2. 同人种优先
            int raceCmp = -Boolean.compare(isSameRace(a.getRace(), userRace), isSameRace(b.getRace(), userRace));
            if (raceCmp != 0) return raceCmp;
            // 3. 年龄差小优先
            int ageCmp = Integer.compare(Math.abs(a.getAge() - userAge), Math.abs(b.getAge() - userAge));
            if (ageCmp != 0) return ageCmp;
            // 4. 颜值分高优先
            return -Integer.compare(a.getBeautyScore(), b.getBeautyScore());
        });
    }

    private boolean isSameRace(String candidateRace, String userRace) {
        if (candidateRace == null || userRace == null) return false;
        return candidateRace.equalsIgnoreCase(userRace);
    }

    // ═══════════════════════════════════════════
    //  按比例 merge
    // ═══════════════════════════════════════════

    /**
     * 按 bh_ratio 比例 merge BH/DH 两池，交错插入
     *
     * PRD 4.2.5:
     *   target_bh = round(240 * bh_ratio)
     *   target_dh = 240 - target_bh
     *   actual_bh = min(target_bh, len(BH_queue))
     *   short = target_bh - actual_bh → DH 补齐
     *   interleave: 每 round(1/bh_ratio) 张 DH 间塞 1 张 BH
     */
    private List<CardEntry> mergePools(List<NearbyUser> bhPool, List<DhCandidate> dhPool) {
        int targetBh = (int) Math.round(QUEUE_SIZE * bhRatio);
        int targetDh = QUEUE_SIZE - targetBh;

        int actualBh = Math.min(targetBh, bhPool.size());
        int shortage = targetBh - actualBh; // BH 不足，由 DH 顶替
        int actualDh = Math.min(targetDh + shortage, dhPool.size());

        // 最终容量 = actualBh + actualDh
        List<CardEntry> result = new ArrayList<>(actualBh + actualDh);

        // 交错插入：每 n 张 DH 间塞 1 张 BH
        int interleaveStep = bhRatio > 0 ? (int) Math.round(1.0 / bhRatio) : Integer.MAX_VALUE;

        int bhIdx = 0, dhIdx = 0;
        while (bhIdx < actualBh || dhIdx < actualDh) {
            // 插入一张 BH
            if (bhIdx < actualBh) {
                NearbyUser u = bhPool.get(bhIdx++);
                result.add(new CardEntry(u.getUserId(), Constants.USER_TYPE_BH));
            }
            // 插入 n 张 DH
            for (int i = 0; i < interleaveStep - 1 && dhIdx < actualDh; i++) {
                DhCandidate c = dhPool.get(dhIdx++);
                result.add(new CardEntry(c.getUserId(), Constants.USER_TYPE_DH));
            }
        }

        // 补剩余 DH（BH 用完后）
        while (dhIdx < actualDh) {
            DhCandidate c = dhPool.get(dhIdx++);
            result.add(new CardEntry(c.getUserId(), Constants.USER_TYPE_DH));
        }

        return result;
    }

    // ═══════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════

    private Set<Long> getSwipedTargets(Long userId) {
        try {
            List<Long> swipedIds = swipeHistoryManager.listSwipedUserIds(userId);
            return swipedIds != null ? new HashSet<>(swipedIds) : Collections.emptySet();
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
