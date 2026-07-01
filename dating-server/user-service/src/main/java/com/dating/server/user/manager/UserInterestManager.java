package com.dating.server.user.manager;

import com.dating.server.user.constant.CacheKeys;
import com.dating.server.user.entity.UserInterest;
import com.dating.server.user.mapper.UserInterestMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 兴趣标签 Manager
 *
 * 读：先查 Redis 缓存 → 没命中查 DB → 写入缓存
 * 写：删 DB → 删缓存
 * 设计文档 §5.6
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserInterestManager {

    private final UserInterestMapper mapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** Redis 中兴趣缓存的 TTL：7 天（兴趣不常变） */
    private static final long INTEREST_CACHE_TTL_DAYS = 7;

    /**
     * 带缓存查询：查用户的兴趣标签列表
     *
     * 流程：
     * 1. 查 Redis key="user:interest:{userId}"，有就直接返回
     * 2. 没有就查 DB，查到后写入 Redis（7 天过期）
     */
    public List<UserInterest> getCachedByUserId(Long userId) {

        // 第1步：查缓存
        String cacheKey = CacheKeys.interest(userId);
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cachedJson != null) {
            try {
                // 把 JSON 反序列化成 List<UserInterest>
                List<UserInterest> cached = objectMapper.readValue(
                        cachedJson, new TypeReference<List<UserInterest>>() {});
                log.debug("兴趣缓存命中: key={}", cacheKey);
                return cached;
            } catch (Exception e) {
                log.warn("兴趣缓存 JSON 解析失败: key={}", cacheKey, e);
            }
        }

        // 第2步：查数据库
        List<UserInterest> interests = getByUserId(userId);

        if (!interests.isEmpty()) {
            // 写入 Redis 缓存
            try {
                String json = objectMapper.writeValueAsString(interests);
                stringRedisTemplate.opsForValue().set(
                        cacheKey, json, INTEREST_CACHE_TTL_DAYS, TimeUnit.DAYS);
                log.debug("兴趣缓存写入: key={}", cacheKey);
            } catch (Exception e) {
                log.warn("兴趣缓存写入失败: key={}", cacheKey, e);
            }
        }

        return interests;
    }

    /** 直接查 DB（不走缓存），按 sortOrder 升序 */
    public List<UserInterest> getByUserId(Long userId) {
        return mapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserInterest>()
                        .eq(UserInterest::getUserId, userId)
                        .orderByAsc(UserInterest::getSortOrder)
        );
    }

    /** 批量查 DB（不走缓存），一次 IN 查询多个用户，按 userId + sortOrder 分组排序 */
    public List<UserInterest> getByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return mapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserInterest>()
                        .in(UserInterest::getUserId, userIds)
                        .orderByAsc(UserInterest::getUserId, UserInterest::getSortOrder)
        );
    }

    /**
     * 全量替换兴趣标签（先删后插）
     * 注意：这个操作在 Service 层事务内执行
     */
    public void replaceAll(Long userId, List<UserInterest> newInterests) {
        mapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserInterest>()
                        .eq(UserInterest::getUserId, userId)
        );
        if (!newInterests.isEmpty()) {
            for (UserInterest interest : newInterests) {
                interest.setUserId(userId);
                mapper.insert(interest);
            }
        }
    }

    /** 删除用户的所有兴趣标签 */
    public void deleteByUserId(Long userId) {
        mapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserInterest>()
                        .eq(UserInterest::getUserId, userId)
        );
    }

    /**
     * 删除 Redis 中的兴趣缓存
     * 在用户修改兴趣后调用，保证下次查询能看到最新数据
     */
    public void evictInterestCache(Long userId) {
        String cacheKey = CacheKeys.interest(userId);
        stringRedisTemplate.delete(cacheKey);
        log.debug("兴趣缓存已删除: key={}", cacheKey);
    }
}
