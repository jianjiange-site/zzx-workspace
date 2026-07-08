package com.dating.server.match.mq;

import com.dating.proto.user.DhCandidate;
import com.dating.server.match.client.ImServiceClient;
import com.dating.server.match.client.UserServiceClient;
import com.dating.server.match.constant.CacheKeys;
import com.dating.server.match.constant.Constants;
import com.dating.server.match.entity.DhInteractionTask;
import com.dating.server.match.mapper.DhInteractionTaskMapper;
import com.dating.server.match.manager.LikeRecordManager;
import com.dating.server.match.manager.MatchManager;
import com.dating.server.match.manager.VisitRecordManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * OfflinePlanGenerator（PRD 6.3.2）
 *
 * 每 20 分钟扫离线用户区间，为每个通过过滤的 BH 用户生成 DH like/visit 任务，
 * 用户下次打开 App 时看到"我不在的时候有人喜欢/访问了我"。
 *
 * Redisson 锁：lock:match:dh_plan:offline_sweep（TODO: 接入分布式锁）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflinePlanGenerator {

    private final ImServiceClient imServiceClient;
    private final UserServiceClient userServiceClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final DhInteractionTaskMapper dhInteractionTaskMapper;
    private final LikeRecordManager likeRecordManager;
    private final VisitRecordManager visitRecordManager;
    private final MatchManager matchManager;

    // ── Nacos 可调参数 ──
    @Value("${match.dh_plan.offline_count_min:3}")
    private int offlineCountMin;
    @Value("${match.dh_plan.offline_count_max:6}")
    private int offlineCountMax;
    @Value("${match.dh_plan.offline_threshold_seconds:1200}")
    private long offlineThresholdSeconds;     // 20 min
    @Value("${match.dh_plan.offline_lookback_seconds:10800}")
    private long offlineLookbackSeconds;      // 3h
    @Value("${match.dh_plan.offline_execute_window_min:30}")
    private int offlineExecuteWindowMin;
    @Value("${match.dh_plan.visit_ratio:0.6}")
    private double visitRatio;

    /** 单次扫描上限 */
    private static final int SCAN_LIMIT = 5000;

    @Scheduled(fixedDelay = 1_200_000) // 每 20 分钟
    public void run() {
        log.debug("OfflinePlanGenerator 开始扫描");

        // a. 游标管理
        String cursorKey = CacheKeys.cursorOffline();
        String cursorStr = stringRedisTemplate.opsForValue().get(cursorKey);
        long now = Instant.now().toEpochMilli();
        long cursor = cursorStr != null ? Long.parseLong(cursorStr) : (now - offlineLookbackSeconds * 1000);

        // minLowerBound = max(cursor, now - 3h)
        long minBound = now - offlineLookbackSeconds * 1000;
        long sinceMs = Math.max(cursor, minBound);
        long untilMs = now - offlineThresholdSeconds * 1000; // 真正离线满 20 分钟才算

        if (sinceMs >= untilMs) {
            log.debug("OfflinePlanGenerator 无有效窗口: sinceMs={}, untilMs={}", sinceMs, untilMs);
            return;
        }

        // b. 拉离线用户
        List<Long> offlineUserIds = imServiceClient.listRecentOfflineUsers(sinceMs, untilMs, SCAN_LIMIT);

        // c. 推进游标（留 20 分钟重叠窗口）
        stringRedisTemplate.opsForValue().set(cursorKey, String.valueOf(now - offlineThresholdSeconds * 1000));

        if (offlineUserIds.isEmpty()) {
            log.debug("OfflinePlanGenerator 无离线用户");
            return;
        }

        log.info("OfflinePlanGenerator: 扫到 {} 个离线用户", offlineUserIds.size());

        // d. 过滤 + 生成
        int generated = 0;
        for (Long userId : offlineUserIds) {
            try {
                if (generateTasksForUser(userId)) {
                    generated++;
                }
            } catch (Exception e) {
                log.error("OfflinePlanGenerator 处理用户异常: userId={}", userId, e);
            }
        }

        log.info("OfflinePlanGenerator 完成: 离线用户={}, 生成计划={}", offlineUserIds.size(), generated);
    }

    /**
     * 为单个离线用户生成 DH 互动任务
     */
    private boolean generateTasksForUser(Long userId) {
        // 闸1: lastScene 闸 — 本次离线期内已生成过 OFFLINE 计划则跳过
        String lastScene = stringRedisTemplate.opsForValue().get(CacheKeys.lastScene(userId));
        if ("OFFLINE".equals(lastScene)) {
            log.debug("OfflinePlanGenerator 跳过(已生成 OFFLINE 计划): userId={}", userId);
            return false;
        }

        // 闸2: 任务表去重 — 已有未执行的 OFFLINE 任务跳过
        Long existingTask = dhInteractionTaskMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DhInteractionTask>()
                        .eq(DhInteractionTask::getToUserId, userId)
                        .eq(DhInteractionTask::getScene, Constants.DH_SCENE_OFFLINE)
                        .last("LIMIT 1"));
        if (existingTask != null && existingTask > 0) {
            log.debug("OfflinePlanGenerator 跳过(已有待执行 OFFLINE 任务): userId={}", userId);
            return false;
        }

        // 闸3: 类型闸 — 假设 im-service 返回的 userId 天然是 BH
        // 不需要 cooldown 检查（PRD 6.3.2 明确说明"没有 cooldown 检查"）

        // d. 生成 DH 候选
        int targetGender = oppositeGender(getUserGender(userId));
        int dhCount = ThreadLocalRandom.current().nextInt(offlineCountMin, offlineCountMax + 1);
        List<Long> excludeUserIds = getExcludeUserIds(userId);

        List<DhCandidate> candidates = userServiceClient.listDhCandidates(
                targetGender, 0, 999, 0, 999,
                Collections.emptyList(), excludeUserIds, dhCount);

        if (candidates.isEmpty()) {
            log.debug("OfflinePlanGenerator 跳过(无 DH 候选): userId={}", userId);
            return false;
        }

        // e. 生成任务行
        List<DhInteractionTask> tasks = new ArrayList<>();
        long now = Instant.now().toEpochMilli();
        long windowMs = (long) offlineExecuteWindowMin * 60 * 1000;

        for (DhCandidate dh : candidates) {
            DhInteractionTask task = new DhInteractionTask();
            task.setFromUserId(dh.getUserId());
            task.setToUserId(userId);
            task.setScene(Constants.DH_SCENE_OFFLINE);

            if (ThreadLocalRandom.current().nextDouble() < visitRatio) {
                task.setAction(Constants.DH_ACTION_VISIT);
            } else {
                task.setAction(Constants.DH_ACTION_LIKE);
                task.setLikeContent(pickRandomLikeContent(targetGender));
            }

            long executeTimeMs = now + ThreadLocalRandom.current().nextLong(0, windowMs);
            task.setExecuteTime(Instant.ofEpochMilli(executeTimeMs));

            tasks.add(task);
        }

        for (DhInteractionTask task : tasks) {
            dhInteractionTaskMapper.insert(task);
        }

        // 收尾: 写 lastScene = OFFLINE（不写 cooldown）
        stringRedisTemplate.opsForValue().set(
                CacheKeys.lastScene(userId), "OFFLINE");

        log.info("OfflinePlanGenerator 生成 {} 个任务: toUserId={}", tasks.size(), userId);
        return true;
    }

    // ═══════════════════════════
    //  辅助方法
    // ═══════════════════════════

    private List<Long> getExcludeUserIds(Long bhUserId) {
        Set<Long> excluded = new HashSet<>();
        try {
            likeRecordManager.listByToUser(bhUserId)
                    .forEach(lr -> excluded.add(lr.getFromUserId()));
            visitRecordManager.listByToUser(bhUserId)
                    .forEach(vr -> excluded.add(vr.getFromUserId()));
            matchManager.listByUserId(bhUserId).forEach(m -> {
                long peer = m.getUserIdLow().equals(bhUserId)
                        ? m.getUserIdHigh() : m.getUserIdLow();
                excluded.add(peer);
            });
        } catch (Exception e) {
            log.warn("获取 excludeUserIds 失败: bhUserId={}", bhUserId, e);
        }
        excluded.remove(bhUserId);
        return new ArrayList<>(excluded);
    }

    private int getUserGender(Long userId) {
        try {
            var profile = userServiceClient.getProfile(userId);
            if (profile != null) return profile.getGender();
        } catch (Exception e) {
            log.warn("获取用户性别失败: userId={}", userId, e);
        }
        return 0;
    }

    private int oppositeGender(int gender) {
        return gender == 1 ? 2 : (gender == 2 ? 1 : 0);
    }

    private String pickRandomLikeContent(int targetGender) {
        String[] templates = {
                "你的照片好有气质！",
                "感觉我们会很聊得来～",
                "你看起来很有趣！",
                "你好，想认识你一下",
                "你的笑容很温暖",
        };
        return templates[ThreadLocalRandom.current().nextInt(templates.length)];
    }
}
