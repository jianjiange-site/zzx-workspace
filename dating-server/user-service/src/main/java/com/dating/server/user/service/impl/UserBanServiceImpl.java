package com.dating.server.user.service.impl;

import com.dating.server.user.constant.CacheKeys;
import com.dating.server.user.constant.RegulationStatus;
import com.dating.server.user.dto.BanResult;
import com.dating.server.user.manager.UserInfoManager;
import com.dating.server.user.service.UserBanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 封禁查询服务实现
 *
 * 功能：检查用户是否被封禁。
 *
 * 缓存策略：查一次后把结果缓存在 Redis 5 分钟
 *   - key: user:ban:status:{userId}
 *   - 原因：登录场景高频调用，5 分钟的延迟可以接受
 *
 * 封禁来源有两种：
 *   1. 用户级封禁 → user_info.regulation_status 字段（DB 2=封禁, 5=暂停）
 *   2. 运营级封禁 → Redis Set user:ban:operational（由运营后台写入）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBanServiceImpl implements UserBanService {

    private final UserInfoManager userInfoManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 封禁缓存 TTL：5 分钟 */
    private static final long BAN_CACHE_TTL_MINUTES = 5;

    /**
     * ===== 封禁检查（带缓存） =====
     *
     * 流程：
     * 第1步：查 Redis 缓存 user:ban:status:{userId}
     *   - 有缓存 → 直接返回
     * 第2步：查数据库 regulation_status
     * 第3步：把结果写入 Redis（5 分钟过期）
     */
    @Override
    public BanResult checkBan(Long userId) {

        // === 第1步：查 Redis 缓存 ===
        String cacheKey = CacheKeys.banStatus(userId);
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cachedJson != null) {
            try {
                BanResult cached = objectMapper.readValue(cachedJson, BanResult.class);
                log.debug("封禁缓存命中: userId={}", userId);
                return cached;
            } catch (Exception e) {
                log.warn("封禁缓存解析失败，重新查询: userId={}", userId, e);
            }
        }

        // === 第2步：查用户监管状态 ===
        Integer status = userInfoManager.getRegulationStatus(userId);

        BanResult result;

        if (status == null) {
            // 用户不存在，不阻止登录
            result = BanResult.ok();
        } else if (RegulationStatus.isBannedOrSuspended(status)) {
            String reason = status == RegulationStatus.BANNED.getDbValue() ? "USER_BANNED" : "USER_SUSPENDED";
            String message = status == RegulationStatus.BANNED.getDbValue()
                    ? "Your account has been banned"
                    : "Your account has been suspended";
            log.warn("封禁检查：用户被封禁, userId={}, status={}", userId, status);
            result = BanResult.banned(reason, null, message);
        } else {
            // === 第3步：查 Redis 运营封禁集合 ===
            Boolean isOpsBanned = stringRedisTemplate.opsForSet().isMember(
                    CacheKeys.banOperationalSet(), String.valueOf(userId));
            if (Boolean.TRUE.equals(isOpsBanned)) {
                log.warn("运营封禁命中: userId={}", userId);
                result = BanResult.banned("OPERATIONAL_BANNED", null,
                        "Your account has been banned by administrator");
            } else {
                result = BanResult.ok();
            }
        }

        // 把结果写入缓存（5 分钟过期）
        try {
            String json = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(cacheKey, json, BAN_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("封禁缓存写入失败: userId={}", userId, e);
        }

        return result;
    }
}
