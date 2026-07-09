package com.dating.server.im.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在线状态服务 —— Redis Hash 存储
 *
 * Key: im:presence:<userId>
 * Field → Value:
 *   status    — ONLINE / AWAY / BUSY / OFFLINE
 *   heartbeat — epoch ms 上次心跳时间
 * TTL: 5 分钟（心跳续期）
 *
 * 同时维护 im:presence:online ZSet（score = 上线时间 epoch ms）
 * 供 match-service 的 ListOnlineUserIds RPC 使用。
 *
 * 5 分钟无心跳自动过期，查询时视为 OFFLINE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImPresenceService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String PREFIX = "im:presence:";
    private static final String ONLINE_ZSET = "im:presence:online";
    private static final long HEARTBEAT_TTL_SECONDS = 300; // 5 分钟
    private static final long OFFLINE_THRESHOLD_MS = 300_000; // 5 分钟

    /** 上报心跳 */
    public void heartbeat(Long userId, String status) {
        String key = key(userId);
        stringRedisTemplate.opsForHash().putAll(key, Map.of(
                "status", status != null ? status : "ONLINE",
                "heartbeat", String.valueOf(Instant.now().toEpochMilli())));
        stringRedisTemplate.expire(key, Duration.ofSeconds(HEARTBEAT_TTL_SECONDS));

        // 同步维护在线 ZSet（供 ListOnlineUserIds 使用）
        // ZADD NX: 只在用户不在 ZSet 中时写入，score = 当前时间
        stringRedisTemplate.opsForZSet().addIfAbsent(ONLINE_ZSET, String.valueOf(userId),
                Instant.now().toEpochMilli());
    }

    /** 查询用户在线状态 */
    public PresenceInfo getPresence(Long userId) {
        String key = key(userId);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return new PresenceInfo(userId, false, 0L, "OFFLINE");
        }
        long heartbeat = Long.parseLong((String) entries.getOrDefault("heartbeat", "0"));
        boolean online = (Instant.now().toEpochMilli() - heartbeat) < OFFLINE_THRESHOLD_MS;
        String status = online ? (String) entries.getOrDefault("status", "ONLINE") : "OFFLINE";
        return new PresenceInfo(userId, online, heartbeat, status);
    }

    /** 批量查询 */
    public List<PresenceInfo> batchGetPresence(List<Long> userIds) {
        return userIds.stream().map(this::getPresence).collect(Collectors.toList());
    }

    // ── 新增：供 match-service 使用 ──

    /**
     * 获取在线用户 ID 列表（读 im:presence:online ZSet）
     *
     * @param sinceMs 闭区间下界 (epoch ms)
     * @param untilMs 开区间上界
     * @param limit   返回上限
     */
    public List<Long> listOnlineUserIds(long sinceMs, long untilMs, int limit) {
        try {
            // ZRANGEBYSCORE im:presence:online sinceMs untilMs LIMIT 0 limit
            Set<String> members = stringRedisTemplate.opsForZSet()
                    .rangeByScore(ONLINE_ZSET, sinceMs, untilMs, 0, limit);
            if (members == null || members.isEmpty()) return Collections.emptyList();
            return members.stream().map(Long::valueOf).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("ListOnlineUserIds 失败: sinceMs={}, untilMs={}", sinceMs, untilMs, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取最近离线用户 ID 列表
     *
     * 当前简化实现：查 ZSet 中不在 [sinceMs, untilMs) 范围内且已过期（无 Heartbeat Hash）的用户。
     * TODO: 接入 OpenIM 上下线回调 + user_online_session PG 表后替换为 PG 查询
     */
    public List<Long> listRecentOfflineUsers(long sinceMs, long untilMs, int limit) {
        try {
            // 当前简化策略：从 online ZSet 中移除 score < untilMs 的用户（视为已离线）
            // 并返回这些用户 ID（仅当他们的 Hash 已过期或不存在时）
            Set<String> candidates = stringRedisTemplate.opsForZSet()
                    .rangeByScore(ONLINE_ZSET, 0, untilMs, 0, limit);

            if (candidates == null || candidates.isEmpty()) return Collections.emptyList();

            // 过滤：只有心 beat Hash 已过期（不存在）的用户才算离线
            List<Long> offlineUsers = candidates.stream()
                    .filter(id -> {
                        String hashKey = key(Long.valueOf(id));
                        return Boolean.FALSE.equals(stringRedisTemplate.hasKey(hashKey));
                    })
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            // 从 ZSet 中清理这些已确认离线的用户
            if (!offlineUsers.isEmpty()) {
                stringRedisTemplate.opsForZSet().remove(ONLINE_ZSET,
                        offlineUsers.stream().map(String::valueOf).toArray());
            }

            return offlineUsers;
        } catch (Exception e) {
            log.error("ListRecentOfflineUsers 失败: sinceMs={}, untilMs={}", sinceMs, untilMs, e);
            return Collections.emptyList();
        }
    }

    // ── OpenIM 集成 ──

    private OpenImService openImService;

    @Autowired
    public void setOpenImService(OpenImService openImService) {
        this.openImService = openImService;
    }

    public String ensureConversation(Long userIdA, Long userIdB) {
        log.info("ENSURE_CONVERSATION: userIdA={}, userIdB={}", userIdA, userIdB);
        return openImService.ensureConversation(userIdA, userIdB);
    }

    public boolean sendSystemMessage(Long toUserId, String title, String body) {
        log.info("SEND_SYSTEM_MSG: toUserId={}, title={}, body={}", toUserId, title, body);
        return openImService.sendSystemMessage(toUserId, title, body);
    }

    public boolean triggerDhOpening(Long dhUserId, Long targetUserId) {
        log.info("TRIGGER_DH_OPENING: dhUserId={}, targetUserId={}", dhUserId, targetUserId);
        // 暂为 stub：待 ai-chat gRPC 集成后实现
        return true;
    }

    private String key(Long userId) {
        return PREFIX + userId;
    }

    public record PresenceInfo(long userId, boolean online, long lastHeartbeatAt, String status) {}
}
