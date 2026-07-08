package com.dating.server.match.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.match.constant.Constants;
import com.dating.server.match.entity.DhInteractionTask;
import com.dating.server.match.entity.LikeRecord;
import com.dating.server.match.entity.Match;
import com.dating.server.match.entity.MatchOutbox;
import com.dating.server.match.entity.VisitRecord;
import com.dating.server.match.manager.LikeRecordManager;
import com.dating.server.match.manager.MatchManager;
import com.dating.server.match.manager.MatchOutboxManager;
import com.dating.server.match.manager.VisitRecordManager;
import com.dating.server.match.mapper.DhInteractionTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * DH 互动任务调度器
 *
 * 每 30 秒轮询 dh_interaction_task 表中到期的任务（execute_time <= now）：
 *   action=1 LIKE   → 创建 like_record + 双向检测（可能触发 match）
 *   action=2 VISIT  → 创建 visit_record
 *
 * 执行后硬删（短生命周期任务，PRD 5.2）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DhInteractionSchedulerJob {

    private final DhInteractionTaskMapper dhInteractionTaskMapper;
    private final LikeRecordManager likeRecordManager;
    private final MatchManager matchManager;
    private final MatchOutboxManager matchOutboxManager;
    private final VisitRecordManager visitRecordManager;

    private static final int BATCH_SIZE = 50;

    @Scheduled(fixedDelay = 30_000) // 30 秒
    public void processDueTasks() {
        List<DhInteractionTask> tasks = dhInteractionTaskMapper.selectList(
                new LambdaQueryWrapper<DhInteractionTask>()
                        .le(DhInteractionTask::getExecuteTime, Instant.now())
                        .orderByAsc(DhInteractionTask::getExecuteTime)
                        .last("LIMIT " + BATCH_SIZE));

        if (tasks.isEmpty()) return;
        log.info("DH 互动调度: 取到 {} 条到期任务", tasks.size());

        for (DhInteractionTask task : tasks) {
            try {
                processTask(task);
                dhInteractionTaskMapper.deleteById(task.getId());
            } catch (Exception e) {
                log.error("DH 互动任务处理失败: id={}, action={}, from={}, to={}",
                        task.getId(), task.getAction(), task.getFromUserId(), task.getToUserId(), e);
            }
        }
    }

    @Transactional
    protected void processTask(DhInteractionTask task) {
        if (task.getAction() == null) return;

        switch (task.getAction()) {
            case 1 -> processLike(task);  // LIKE
            case 2 -> processVisit(task); // VISIT
            default -> log.warn("未知 DH 互动 action: {}", task.getAction());
        }
    }

    /** 处理 LIKE 动作（DH → BH） */
    private void processLike(DhInteractionTask task) {
        // 幂等：已存在则跳过
        LikeRecord existing = likeRecordManager.getByFromTo(task.getFromUserId(), task.getToUserId());
        if (existing != null) return;

        // 创建 like_record（DH 喜欢 BH）
        LikeRecord like = new LikeRecord();
        like.setFromUserId(task.getFromUserId());
        like.setToUserId(task.getToUserId());
        like.setFromUserType(Constants.USER_TYPE_DH);
        like.setSource(Constants.LIKE_SOURCE_SWIPE);
        like.setLikeContent(task.getLikeContent());
        likeRecordManager.insert(like);

        // 双向检测：BH 是否已右划过该 DH
        LikeRecord reciprocal = likeRecordManager.getByFromTo(task.getToUserId(), task.getFromUserId());
        if (reciprocal != null) {
            createMatch(task.getToUserId(), task.getFromUserId(), Constants.MATCH_SOURCE_SWIPE);
        }

        log.info("DH LIKE 处理完成: from={}, to={}, content={}",
                task.getFromUserId(), task.getToUserId(), task.getLikeContent());
    }

    /** 处理 VISIT 动作（DH → BH） */
    private void processVisit(DhInteractionTask task) {
        // 幂等：已有最近访问记录则递增（24h 内）
        VisitRecord existing = visitRecordManager.getByFromTo(task.getFromUserId(), task.getToUserId());
        if (existing != null && existing.getVisitedAt() != null
                && existing.getVisitedAt().isAfter(Instant.now().minusSeconds(86400))) {
            visitRecordManager.incrementVisit(existing);
            return;
        }

        VisitRecord visit = new VisitRecord();
        visit.setFromUserId(task.getFromUserId());
        visit.setToUserId(task.getToUserId());
        visit.setFromUserType(Constants.USER_TYPE_DH);
        visit.setSource(task.getScene());
        visitRecordManager.insert(visit);

        log.info("DH VISIT 处理完成: from={}, to={}", task.getFromUserId(), task.getToUserId());
    }

    /** 创建匹配（复用 createMatch 逻辑） */
    private void createMatch(Long userIdA, Long userIdB, String source) {
        long low = Math.min(userIdA, userIdB);
        long high = Math.max(userIdA, userIdB);

        Match existing = matchManager.getByUserPair(low, high);
        if (existing != null) return;

        Match match = new Match();
        match.setUserIdLow(low);
        match.setUserIdHigh(high);
        match.setMatchedAt(Instant.now());
        match.setSource(source);
        matchManager.insert(match);

        likeRecordManager.deleteByFromTo(userIdA, userIdB);
        likeRecordManager.deleteByFromTo(userIdB, userIdA);

        MatchOutbox outbox = new MatchOutbox();
        outbox.setMatchId(match.getId());
        outbox.setAction("ENSURE_CONVERSATION");
        outbox.setPayloadJson("{\"match_id\":" + match.getId() + "}");
        outbox.setAttempts(0);
        outbox.setNextRetryAt(Instant.now());
        outbox.setStatus("PENDING");
        matchOutboxManager.insert(outbox);

        log.info("DH 调度 match 已创建: matchId={}, userIdA={}, userIdB={}", match.getId(), userIdA, userIdB);
    }
}
