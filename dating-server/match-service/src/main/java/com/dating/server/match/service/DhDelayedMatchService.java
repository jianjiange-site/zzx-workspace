package com.dating.server.match.service;

import com.dating.server.match.constant.Constants;
import com.dating.server.match.entity.Match;
import com.dating.server.match.entity.MatchOutbox;
import com.dating.server.match.manager.MatchManager;
import com.dating.server.match.manager.MatchOutboxManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DH 延迟匹配服务 —— 用户右划 DH 后，延迟 15s~2min 再创建 match
 *
 * 核心问题：如果用户右划 DH 后一秒就配对，用户立刻意识到是 bot。
 * 等太久（> 分钟级）用户已划下一张，失去"她也喜欢我"的即时反馈。
 *
 * 延迟窗口 15s ~ 2min，均匀随机分布。
 * 使用进程内 Spring TaskScheduler 调度（不走 PG 表），
 * 重启丢失 in-flight 任务是接受的 trade-off（PRD 5.2）。
 *
 * 适用范围：仅 BH→DH 普通 RIGHT_SWIPE。
 * Super Hi→DH 走 5.3 立即 match 流程，不走这里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DhDelayedMatchService {

    private final TaskScheduler taskScheduler;
    private final MatchManager matchManager;
    private final MatchOutboxManager matchOutboxManager;

    /** 延迟窗口下限（毫秒） */
    private static final long DELAY_MIN_MS = 15_000;
    /** 延迟窗口上限（毫秒） */
    private static final long DELAY_MAX_MS = 120_000;

    /**
     * 用户右划 DH 后调用，挂一个 15s-2min 后的回调
     *
     * @param userId BH 用户 ID
     * @param dhId   DH 用户 ID
     * @param source MATCH_SOURCE_SWIPE
     */
    public void scheduleDelayedMatch(Long userId, Long dhId, String source) {
        long delayMs = ThreadLocalRandom.current().nextLong(DELAY_MIN_MS, DELAY_MAX_MS);
        Instant executeAt = Instant.now().plusMillis(delayMs);

        taskScheduler.schedule(() -> {
            try {
                createMatchInternal(userId, dhId, source);
            } catch (Exception e) {
                log.error("DH 延迟 match 回调失败: userId={}, dhId={}, delayMs={}",
                        userId, dhId, delayMs, e);
            }
        }, executeAt);

        log.info("DH 延迟 match 已调度: userId={}, dhId={}, delayMs={}ms, executeAt={}",
                userId, dhId, delayMs, executeAt);
    }

    /**
     * 内部创建 match（与 MatchServiceImpl.createMatch 逻辑一致）
     *
     * 幂等：UNIQUE(user_id_low, user_id_high) 兜底
     */
    private void createMatchInternal(Long userIdA, Long userIdB, String source) {
        long low = Math.min(userIdA, userIdB);
        long high = Math.max(userIdA, userIdB);

        Match existing = matchManager.getByUserPair(low, high);
        if (existing != null) {
            log.info("DH 延迟 match 已存在，跳过: pair=({}, {}), matchId={}",
                    low, high, existing.getId());
            return;
        }

        Match match = new Match();
        match.setUserIdLow(low);
        match.setUserIdHigh(high);
        match.setMatchedAt(Instant.now());
        match.setSource(source);
        matchManager.insert(match);

        // 写 outbox（IM 会话创建等副作用由 MatchOutboxRetry 兜底）
        MatchOutbox outbox = new MatchOutbox();
        outbox.setMatchId(match.getId());
        outbox.setAction("ENSURE_CONVERSATION");
        outbox.setPayloadJson("{\"match_id\":" + match.getId() + "}");
        outbox.setAttempts(0);
        outbox.setNextRetryAt(Instant.now());
        outbox.setStatus("PENDING");
        matchOutboxManager.insert(outbox);

        log.info("DH 延迟 match 已创建: matchId={}, userIdA={}, userIdB={}, source={}",
                match.getId(), userIdA, userIdB, source);
    }
}
