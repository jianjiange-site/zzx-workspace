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
 * OnlinePlanGenerator（PRD 6.3.1）
 *
 * 每 1 分钟扫在线用户增量，为每个通过过滤的 BH 用户生成 DH like/visit 任务，
 * 营造"涓涓细流"的关注感。
 *
 * Redisson 锁：lock:match:dh_plan:online_sweep（TODO: 接入分布式锁）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnlinePlanGenerator {

    private final ImServiceClient imServiceClient;
    private final UserServiceClient userServiceClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final DhInteractionTaskMapper dhInteractionTaskMapper;
    private final LikeRecordManager likeRecordManager;
    private final VisitRecordManager visitRecordManager;
    private final MatchManager matchManager;

    // ── Nacos 可调参数 ──
    @Value("${match.dh_plan.online_count_min:5}")
    private int onlineCountMin;
    @Value("${match.dh_plan.online_count_max:10}")
    private int onlineCountMax;
    @Value("${match.dh_plan.online_cooldown_seconds:7200}")
    private long onlineCooldownSeconds;
    @Value("${match.dh_plan.online_execute_window_min:30}")
    private int onlineExecuteWindowMin;
    @Value("${match.dh_plan.visit_ratio:0.6}")
    private double visitRatio;
    @Value("${match.dh_plan.offline_threshold_seconds:1200}")
    private long offlineThresholdSeconds;
    @Value("${match.dh_plan.max_lookback_seconds:1800}")
    private long maxLookbackSeconds; // 30 min

    /** 单次扫描上限 */
    private static final int SCAN_LIMIT = 5000;

    @Scheduled(fixedDelay = 60_000) // 每 1 分钟
    public void run() {
        log.debug("OnlinePlanGenerator 开始扫描");

        // a. 游标管理
        String cursorKey = CacheKeys.cursorOnline();
        String cursorStr = stringRedisTemplate.opsForValue().get(cursorKey);
        long now = Instant.now().toEpochMilli();
        long cursor = cursorStr != null ? Long.parseLong(cursorStr) : (now - 60_000);

        // 最大回看 30 分钟
        long minBound = now - maxLookbackSeconds * 1000;
        if (cursor < minBound) {
            log.warn("OnlinePlanGenerator 游标超过最大回看窗口, 重置: cursor={}, now={}", cursor, now);
            cursor = now - 60_000;
        }

        if (cursor >= now) {
            log.debug("OnlinePlanGenerator 无新窗口: cursor={}, now={}", cursor, now);
            return;
        }

        // b. 拉在线用户
        List<Long> onlineUserIds = imServiceClient.listOnlineUserIds(cursor, now, SCAN_LIMIT);

        // c. 推进游标
        stringRedisTemplate.opsForValue().set(cursorKey, String.valueOf(now));

        if (onlineUserIds.isEmpty()) {
            log.debug("OnlinePlanGenerator 无在线用户");
            return;
        }

        log.info("OnlinePlanGenerator: 扫到 {} 个在线用户", onlineUserIds.size());

        // d. 过滤 + 生成
        int generated = 0;
        for (Long userId : onlineUserIds) {
            try {
                if (generateTasksForUser(userId)) {
                    generated++;
                }
            } catch (Exception e) {
                log.error("OnlinePlanGenerator 处理用户异常: userId={}", userId, e);
            }
        }

        log.info("OnlinePlanGenerator 完成: 在线用户={}, 生成计划={}", onlineUserIds.size(), generated);
    }

    /**
     * 为单个用户生成 DH 互动任务
     *
     * @return true=成功生成, false=被过滤跳过
     */
    private boolean generateTasksForUser(Long userId) {
        // 闸1: Cooldown 检查
        String cooldownKey = CacheKeys.cooldown(userId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey))) {
            log.debug("OnlinePlanGenerator 跳过(冷却中): userId={}", userId);
            return false;
        }

        // 闸2: 任务表去重 — 已有未执行的 ONLINE 任务跳过
        Long existingTask = dhInteractionTaskMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DhInteractionTask>()
                        .eq(DhInteractionTask::getToUserId, userId)
                        .eq(DhInteractionTask::getScene, Constants.DH_SCENE_ONLINE)
                        .last("LIMIT 1"));
        if (existingTask != null && existingTask > 0L) {
            log.debug("OnlinePlanGenerator 跳过(已有待执行任务): userId={}", userId);
            return false;
        }

        // 闸3: 类型闸 — userId 必须是 BH（DH 不接收互动）
        // 暂时假设 im-service 返回的在线用户天然是 BH（DH 没有 IM 设备登录）
        // 如需要可再调 user-service.getProfile 校验 user_type

        // d. 生成 DH 候选
        int targetGender = oppositeGender(getUserGender(userId));
        int dhCount = ThreadLocalRandom.current().nextInt(onlineCountMin, onlineCountMax + 1);
        List<Long> excludeUserIds = getExcludeUserIds(userId);

        List<DhCandidate> candidates = userServiceClient.listDhCandidates(
                targetGender, 0, 999, 0, 999,
                Collections.emptyList(), excludeUserIds, dhCount);

        if (candidates.isEmpty()) {
            log.debug("OnlinePlanGenerator 跳过(无 DH 候选): userId={}", userId);
            return false;
        }

        // e. 生成任务行
        List<DhInteractionTask> tasks = new ArrayList<>();
        long now = Instant.now().toEpochMilli();
        long windowMs = (long) onlineExecuteWindowMin * 60 * 1000;

        for (DhCandidate dh : candidates) {
            DhInteractionTask task = new DhInteractionTask();
            task.setFromUserId(dh.getUserId());
            task.setToUserId(userId);
            task.setScene(Constants.DH_SCENE_ONLINE);

            // 按 visit_ratio 分配 action
            if (ThreadLocalRandom.current().nextDouble() < visitRatio) {
                task.setAction(Constants.DH_ACTION_VISIT);
            } else {
                task.setAction(Constants.DH_ACTION_LIKE);
                task.setLikeContent(pickRandomLikeContent(targetGender));
            }

            // execute_time 在 [now, now+30min] 均匀随机分布
            long executeTimeMs = now + ThreadLocalRandom.current().nextLong(0, windowMs);
            task.setExecuteTime(java.time.Instant.ofEpochMilli(executeTimeMs));

            tasks.add(task);
        }

        // 批量 INSERT
        for (DhInteractionTask task : tasks) {
            dhInteractionTaskMapper.insert(task);
        }

        // 收尾: 写 cooldown
        stringRedisTemplate.opsForValue().set(
                cooldownKey, "1", Duration.ofSeconds(onlineCooldownSeconds));

        // 写 last_scene
        stringRedisTemplate.opsForValue().set(
                CacheKeys.lastScene(userId), "ONLINE");

        log.info("OnlinePlanGenerator 生成 {} 个任务: toUserId={}", tasks.size(), userId);
        return true;
    }

    // ═══════════════════════════
    //  辅助方法
    // ═══════════════════════════

    /**
     * 获取已与目标 BH 关联过的用户 ID 列表（like / visit / match 三路 UNION）
     *
     * PRD 6.4 exclude_user_ids：
     *   - 已 like 过该 BH 的 DH（避免重复内容）
     *   - 已 visit 过该 BH 的 DH
     *   - 已与该 BH match 的用户
     */
    private List<Long> getExcludeUserIds(Long bhUserId) {
        Set<Long> excluded = new HashSet<>();
        try {
            // like_record: 已 like 过该 BH 的 from_user
            likeRecordManager.listByToUser(bhUserId)
                    .forEach(lr -> excluded.add(lr.getFromUserId()));
            // visit_record: 已 visit 过该 BH 的 from_user
            visitRecordManager.listByToUser(bhUserId)
                    .forEach(vr -> excluded.add(vr.getFromUserId()));
            // match: 已与该 BH 配对的用户
            matchManager.listByUserId(bhUserId).forEach(m -> {
                long peer = m.getUserIdLow().equals(bhUserId)
                        ? m.getUserIdHigh() : m.getUserIdLow();
                excluded.add(peer);
            });
        } catch (Exception e) {
            log.warn("获取 excludeUserIds 失败: bhUserId={}", bhUserId, e);
        }
        // 移除自己
        excluded.remove(bhUserId);
        return new ArrayList<>(excluded);
    }

    /** 获取用户性别（简化：从 user-service 查询） */
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

    /** 随机抽取 like_content 模板（TODO: 从 Nacos 读取） */
    private String pickRandomLikeContent(int targetGender) {
        // 临时硬编码模板列表，后续改为 Nacos match.dh_plan.like_content_templates
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
