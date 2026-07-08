package com.dating.server.match.recommend;

import com.dating.proto.user.UserProfile;
import com.dating.server.match.client.UserServiceClient;
import com.dating.server.match.constant.CacheKeys;
import com.dating.server.match.constant.Constants;
import com.dating.server.match.entity.SwipeHistory;
import com.dating.server.match.manager.SwipeHistoryManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 偏好建模（PRD 4.2.1）
 *
 * 从最近 30 天 user_swipe_history 聚合右划画像分布。
 * 样本数 < 10 时回退用用户自身画像（D0 prior）。
 * 结果缓存到 Redis 24h（match:pref:<userId>）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreferenceBuilder {

    private final SwipeHistoryManager swipeHistoryManager;
    private final UserServiceClient userServiceClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final int LOOKBACK_DAYS = 30;
    private static final long CACHE_TTL_SECONDS = 86400; // 24h

    /**
     * 构建用户偏好画像
     *
     * @param userId       目标用户
     * @param selfProfile  用户自身资料（D0 fallback 用）
     * @return 偏好画像（缓存已命中则直接返回缓存）
     */
    public PreferenceProfile build(Long userId, UserProfile selfProfile) {
        // 尝试从缓存读取
        String cacheKey = CacheKeys.pref(userId);
        String cached = (String) stringRedisTemplate.opsForHash().get(cacheKey, "profile");
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, PreferenceProfile.class);
            } catch (Exception e) {
                log.warn("偏好缓存解析失败, 重新构建: userId={}", userId, e);
            }
        }

        // 查询 30 天内右划（RIGHT_SWIPE + SUPER_HI）记录
        Instant since = Instant.now().minusSeconds((long) LOOKBACK_DAYS * 86400);
        List<SwipeHistory> rightSwipes = swipeHistoryManager.listRightSwipesSince(userId, since);

        if (rightSwipes.size() < 10) {
            // 样本不足，回退 D0 prior（用户自身画像）
            PreferenceProfile fallback = buildFromSelf(selfProfile);
            cacheProfile(cacheKey, fallback);
            return fallback;
        }

        // 收集右划 target 的 user_id
        List<Long> targetIds = rightSwipes.stream()
                .map(SwipeHistory::getTargetUserId)
                .distinct()
                .collect(Collectors.toList());

        // 批量查询 target 资料
        Map<Long, UserProfile> profiles = userServiceClient.batchGetProfiles(targetIds);

        // 聚合统计
        double ageSum = 0, beautySum = 0;
        int ageCount = 0, beautyCount = 0;
        Map<String, Integer> raceCounts = new HashMap<>();
        int dhCount = 0;

        for (SwipeHistory s : rightSwipes) {
            UserProfile p = profiles.get(s.getTargetUserId());
            if (p == null) continue;

            if (p.getAge() > 0) {
                ageSum += p.getAge();
                ageCount++;
            }
            if (p.getBeautyScore() > 0) {
                beautySum += p.getBeautyScore();
                beautyCount++;
            }
            if (!p.getRace().isEmpty()) {
                raceCounts.merge(p.getRace(), 1, Integer::sum);
            }
            if (s.getTargetUserType() == Constants.USER_TYPE_DH) {
                dhCount++;
            }
        }

        if (ageCount == 0) {
            return buildFromSelf(selfProfile);
        }

        double ageMean = ageSum / ageCount;
        double beautyMean = beautySum / beautyCount;

        // 标准差
        double ageVar = 0, beautyVar = 0;
        for (SwipeHistory s : rightSwipes) {
            UserProfile p = profiles.get(s.getTargetUserId());
            if (p == null) continue;
            if (p.getAge() > 0) {
                ageVar += Math.pow(p.getAge() - ageMean, 2);
            }
            if (p.getBeautyScore() > 0) {
                beautyVar += Math.pow(p.getBeautyScore() - beautyMean, 2);
            }
        }
        double ageStd = Math.sqrt(ageVar / ageCount);
        double beautyStd = Math.sqrt(beautyVar / beautyCount);

        // 人种分布归一化
        int totalRace = raceCounts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> raceDist = new HashMap<>();
        for (Map.Entry<String, Integer> e : raceCounts.entrySet()) {
            raceDist.put(e.getKey(), (double) e.getValue() / totalRace);
        }

        double dhBhRatio = rightSwipes.isEmpty() ? 0.5 : (double) dhCount / rightSwipes.size();

        PreferenceProfile profile = new PreferenceProfile(
                ageMean, Math.max(ageStd, 1.0),
                beautyMean, Math.max(beautyStd, 1.0),
                raceDist, dhBhRatio, rightSwipes.size());

        cacheProfile(cacheKey, profile);
        return profile;
    }

    /** D0 回退：用用户自身画像作为 prior */
    private PreferenceProfile buildFromSelf(UserProfile self) {
        Map<String, Double> raceDist = new HashMap<>();
        if (!self.getRace().isEmpty()) {
            raceDist.put(self.getRace(), 1.0);
        }
        return new PreferenceProfile(
                self.getAge(), 5.0,           // age std 默认 5
                self.getBeautyScore(), 15.0,   // beauty std 默认 15
                raceDist, 0.5, 0);
    }

    private void cacheProfile(String cacheKey, PreferenceProfile profile) {
        try {
            String json = objectMapper.writeValueAsString(profile);
            stringRedisTemplate.opsForHash().put(cacheKey, "profile", json);
            stringRedisTemplate.expire(cacheKey, Duration.ofSeconds(CACHE_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("偏好缓存序列化失败", e);
        }
    }
}
