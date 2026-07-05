package com.dating.server.user.manager;

import com.dating.server.user.constant.CacheKeys;
import com.dating.server.user.entity.UserInfo;
import com.dating.server.user.mapper.UserInfoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 用户主资料 Manager
 *
 * 两种读数据的方式：
 *   getById()           → 直接查数据库（绕过缓存，适合内部用）
 *   getCachedById()     → 先查 Redis，没命中再查 DB，查完后写入缓存（缓存穿透保护）
 *
 * 写数据：先写 DB，再删缓存（cache aside 模式）
 * 设计文档 §5.5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserInfoManager {

    private final UserInfoMapper userInfoMapper;

    // ===== Redis 缓存 =====
    // StringRedisTemplate：key 和 value 都是字符串，value 存的是 JSON 格式
    private final StringRedisTemplate stringRedisTemplate;
    // ObjectMapper：Spring Boot 自动配置好的 Jackson JSON 工具
    private final ObjectMapper objectMapper;

    /** Redis 缓存中 profile 的过期时间：24 小时 */
    private static final long PROFILE_CACHE_TTL_HOURS = 24;

    // ========================================================================
    // 读操作
    // ========================================================================

    /**
     * 直接查数据库，不走缓存。
     * 用于内部操作（如更新前检查用户是否存在），需要实时数据的时候用这个。
     */
    public UserInfo getById(Long id) {
        return userInfoMapper.selectById(id);
    }

    /**
     * 带缓存的查询 —— 优先读 Redis
     *
     * 流程：
     * 1. 查 Redis key="user:profile:{userId}"，看有没有值
     * 2. 有（缓存命中）→ 把 JSON 反序列化成 UserInfo 对象，直接返回
     * 3. 没有（缓存未命中）→ 查数据库，查到后写入 Redis，再返回
     *
     * 这样下次再查同一个用户就不用走数据库了，加快响应速度。
     */
    public UserInfo getCachedById(Long id) {

        // === 第1步：尝试从 Redis 读取 ===
        String cacheKey = CacheKeys.profile(id);
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cachedJson != null) {
            // 缓存命中：把 JSON 转回 UserInfo 对象
            try {
                UserInfo cached = objectMapper.readValue(cachedJson, UserInfo.class);
                log.debug("缓存命中: key={}", cacheKey);
                return cached;
            } catch (Exception e) {
                // JSON 解析失败，忽略缓存，走 DB 查询
                log.warn("缓存 JSON 解析失败，将重新查询数据库: key={}", cacheKey, e);
            }
        }

        // === 第2步：缓存未命中，查数据库 ===
        UserInfo user = userInfoMapper.selectById(id);

        if (user != null) {
            // 查到数据后，写入 Redis 缓存
            try {
                String json = objectMapper.writeValueAsString(user);
                stringRedisTemplate.opsForValue().set(
                        cacheKey, json, PROFILE_CACHE_TTL_HOURS, TimeUnit.HOURS);
                log.debug("缓存写入: key={}", cacheKey);
            } catch (Exception e) {
                log.warn("缓存写入失败: key={}", cacheKey, e);
            }
        }

        return user;
    }

    /** 批量查询，后续优化加入 Redis 批量读 */
    public java.util.List<UserInfo> getByIds(java.util.Collection<Long> ids) {
        return userInfoMapper.selectBatchIds(ids);
    }

    // ========================================================================
    // 发现服务（match-service 召回用）
    // ========================================================================

    /**
     * 查询 DH 候选列表。
     * 按 user_type=2(DH) + gender + age + beauty_score + race 过滤，
     * 走索引 idx_user_info_user_type_gender_age_beauty。
     */
    public java.util.List<UserInfo> listDhCandidates(int gender, int ageMin, int ageMax,
                                                      int beautyMin, int beautyMax,
                                                      java.util.List<String> races,
                                                      java.util.List<Long> excludeUserIds,
                                                      int limit) {
        LambdaQueryWrapper<UserInfo> q = new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getUserType, 2)           // DH only
                .eq(UserInfo::getGender, gender)
                .ge(UserInfo::getAge, ageMin)
                .le(UserInfo::getAge, ageMax)
                .ge(UserInfo::getBeautyScore, beautyMin)
                .le(UserInfo::getBeautyScore, beautyMax)
                .eq(UserInfo::getPending, false)         // 已补齐资料
                .eq(UserInfo::getRegulationStatus, 0);   // 未封禁

        if (races != null && !races.isEmpty()) {
            q.in(UserInfo::getRace, races);
        }
        if (excludeUserIds != null && !excludeUserIds.isEmpty()) {
            q.notIn(UserInfo::getId, excludeUserIds);
        }
        q.orderByDesc(UserInfo::getBeautyScore)
         .last("LIMIT " + limit);

        return userInfoMapper.selectList(q);
    }

    /**
     * 查询附近 BH 用户。
     * 使用简单的经纬度矩形框近似，精确距离在 service 层计算。
     */
    public java.util.List<UserInfo> nearbyUsers(Long selfUserId, int gender, int ageMin, int ageMax,
                                                  int beautyMin, int beautyMax,
                                                  java.util.List<String> races,
                                                  double lat, double lng, double radiusKm,
                                                  int lastActiveDays, int limit,
                                                  java.util.List<Long> excludeUserIds) {
        // 粗略经纬度范围：1° ≈ 111km
        double deg = radiusKm / 111.0;
        double latMin = lat - deg;
        double latMax = lat + deg;
        double lngMin = lng - deg;
        double lngMax = lng + deg;

        LambdaQueryWrapper<UserInfo> q = new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getUserType, 1)           // BH only
                .eq(UserInfo::getGender, gender)
                .ge(UserInfo::getAge, ageMin)
                .le(UserInfo::getAge, ageMax)
                .ge(UserInfo::getBeautyScore, beautyMin)
                .le(UserInfo::getBeautyScore, beautyMax)
                .ge(UserInfo::getLatitude, latMin)
                .le(UserInfo::getLatitude, latMax)
                .ge(UserInfo::getLongitude, lngMin)
                .le(UserInfo::getLongitude, lngMax)
                .eq(UserInfo::getPending, false)
                .eq(UserInfo::getRegulationStatus, 0);

        if (races != null && !races.isEmpty()) {
            q.in(UserInfo::getRace, races);
        }
        if (excludeUserIds != null && !excludeUserIds.isEmpty()) {
            q.notIn(UserInfo::getId, excludeUserIds);
        }
        if (lastActiveDays > 0) {
            q.ge(UserInfo::getLastOpenAt, java.time.Instant.now().minusSeconds(lastActiveDays * 86400L));
        }
        q.orderByDesc(UserInfo::getBeautyScore)
         .last("LIMIT " + limit);

        return userInfoMapper.selectList(q);
    }

    // ========================================================================
    // 写操作（增/删/改）
    // ========================================================================

    /**
     * 创建占位用户（注册流程用）
     * 字段初始值：pending=true, nickname=User_{id}, gender=0, regulation_status=0
     */
    public UserInfo insertPlaceholder(String appName) {
        UserInfo user = new UserInfo();
        user.setAppName(appName);
        userInfoMapper.insert(user);
        user.setNickname("User_" + user.getId());
        userInfoMapper.updateById(user);
        return user;
    }

    /**
     * 更新最后打开时间
     * 只改 last_open_at 一个字段
     */
    public void touchLastOpenAt(Long userId) {
        UserInfo update = new UserInfo();
        update.setId(userId);
        update.setLastOpenAt(Instant.now());
        userInfoMapper.updateById(update);
        // 缓存不用删，因为 lastOpenAt 不在缓存里（不重要，变化频繁）
    }

    /**
     * 更新资料（cache aside：先写 DB，再删缓存）
     */
    public void updateById(UserInfo user) {
        userInfoMapper.updateById(user);
        evictProfileCache(user.getId());
    }

    /**
     * 更新头像 JSONB
     */
    public void updateCustomAvatar(Long userId, String customAvatarJson) {
        UserInfo update = new UserInfo();
        update.setId(userId);
        update.setCustomAvatar(customAvatarJson);
        userInfoMapper.updateById(update);
        evictProfileCache(userId);
    }

    /**
     * 查询封禁状态（只查 DB，封禁状态由 UserBanService 做缓存）
     */
    public Integer getRegulationStatus(Long userId) {
        UserInfo user = userInfoMapper.selectById(userId);
        return user != null ? user.getRegulationStatus() : null;
    }

    /**
     * 删除 Redis 中的用户资料缓存
     *
     * 在更新资料后调用，这样下次查询时就会重新从 DB 加载最新数据。
     * 这就是 cache aside 模式的关键：先写 DB，再删缓存。
     */
    public void evictProfileCache(Long userId) {
        String cacheKey = CacheKeys.profile(userId);
        stringRedisTemplate.delete(cacheKey);
        log.debug("缓存已删除: key={}", cacheKey);
    }
}
