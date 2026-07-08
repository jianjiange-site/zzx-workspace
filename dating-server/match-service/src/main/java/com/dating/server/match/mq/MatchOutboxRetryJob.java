package com.dating.server.match.mq;

import com.dating.server.match.client.ImServiceClient;
import com.dating.server.match.entity.Match;
import com.dating.server.match.entity.MatchOutbox;
import com.dating.server.match.manager.MatchManager;
import com.dating.server.match.manager.MatchOutboxManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Match Outbox 重试任务
 *
 * 每 30 秒轮询待处理（PENDING + nextRetryAt <= now）的 outbox：
 *   - 成功 → markDone
 *   - 失败 → incrementAttempts + 指数退避（最长 1h）
 *   - 超过 5 次 → markDead
 *
 * 当前 action 处理：
 *   ENSURE_CONVERSATION → 占位（TODO: 调 im-service gRPC 创建会话）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchOutboxRetryJob {

    private final MatchOutboxManager matchOutboxManager;
    private final MatchManager matchManager;
    private final ImServiceClient imServiceClient;

    /** 每次拉取上限 */
    private static final int BATCH_SIZE = 50;
    /** 最大重试次数 */
    private static final int MAX_ATTEMPTS = 5;
    /** 指数退避基数（分钟）：重试 N 次后 nextRetryAt = now + 2^(N-1) 分钟 */
    private static final int BACKOFF_BASE_MINUTES = 2;

    @Scheduled(fixedDelay = 30_000) // 30 秒
    public void processPendingOutbox() {
        List<MatchOutbox> pending = matchOutboxManager.listPending(BATCH_SIZE);
        if (pending.isEmpty()) return;

        log.info("MatchOutbox 重试: 取到 {} 条待处理", pending.size());

        for (MatchOutbox outbox : pending) {
            try {
                boolean ok = handleAction(outbox);
                if (ok) {
                    matchOutboxManager.markDone(outbox.getId());
                    log.debug("Outbox 处理成功: id={}, action={}, matchId={}",
                            outbox.getId(), outbox.getAction(), outbox.getMatchId());
                } else {
                    retryOrDead(outbox);
                }
            } catch (Exception e) {
                log.error("Outbox 处理异常: id={}, action={}", outbox.getId(), outbox.getAction(), e);
                retryOrDead(outbox);
            }
        }
    }

    /** 处理单个 action，返回 true=成功 */
    private boolean handleAction(MatchOutbox outbox) {
        return switch (outbox.getAction()) {
            case "ENSURE_CONVERSATION" -> handleEnsureConversation(outbox);
            default -> {
                log.warn("未知 outbox action: {}", outbox.getAction());
                yield true; // 未知 action 直接标记完成，不阻塞队列
            }
        };
    }

    /**
     * 确保匹配双方已创建 IM 会话
     *
     * 解析 payload_json 中的 match_id 和双端 user_id，调 im-service gRPC 建会话。
     */
    private boolean handleEnsureConversation(MatchOutbox outbox) {
        try {
            Long matchId = outbox.getMatchId();
            Match match = matchManager.getById(matchId);
            if (match == null || match.getDeleted()) {
                log.warn("ENSURE_CONVERSATION match 不存在: matchId={}", matchId);
                return false;
            }

            String convId = imServiceClient.ensureConversation(
                    match.getUserIdLow(), match.getUserIdHigh());
            if (convId != null) {
                log.info("ENSURE_CONVERSATION 成功: matchId={}, convId={}", matchId, convId);

                // 建会话成功后发系统消息给双方
                imServiceClient.sendSystemMessage(match.getUserIdLow(),
                        "新匹配", "你们配对了！快来打个招呼吧");
                imServiceClient.sendSystemMessage(match.getUserIdHigh(),
                        "新匹配", "你们配对了！快来打个招呼吧");

                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("ENSURE_CONVERSATION 异常: outboxId={}", outbox.getId(), e);
            return false;
        }
    }

    /** 重试或死信 */
    private void retryOrDead(MatchOutbox outbox) {
        int attempts = (outbox.getAttempts() != null ? outbox.getAttempts() : 0) + 1;

        if (attempts >= MAX_ATTEMPTS) {
            matchOutboxManager.markDead(outbox.getId());
            log.warn("Outbox 达到最大重试次数, 标记 DEAD: id={}, action={}, attempts={}",
                    outbox.getId(), outbox.getAction(), attempts);
            return;
        }

        // 指数退避：第 N 次重试 → wait 2^(N-1) 分钟
        long backoffMinutes = (long) Math.pow(BACKOFF_BASE_MINUTES, attempts - 1);
        Instant nextRetry = Instant.now().plus(backoffMinutes, ChronoUnit.MINUTES);
        matchOutboxManager.incrementAttempts(outbox.getId(), nextRetry);
        log.warn("Outbox 处理失败, 将重试: id={}, action={}, attempt={}, nextRetry={}",
                outbox.getId(), outbox.getAction(), attempts, nextRetry);
    }
}
