package com.dating.server.match.mq;

import com.dating.server.match.constant.CacheKeys;
import com.dating.server.match.manager.SwipeHistoryManager;
import com.dating.server.match.recommend.D1Generator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * D1 日更队列调度器（PRD 4.2 / 7.5）
 *
 * 每日 UTC 07:00（对应美东 EDT 03:00 / EST 02:00）为昨天有划卡行为的用户生成 D1 队列。
 * 多实例通过 Redisson 锁防重。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class D1QueueScheduler {

    private final D1Generator d1Generator;
    private final SwipeHistoryManager swipeHistoryManager;
    private final RedissonClient redissonClient;

    /** 分布式锁等待时间 */
    private static final long LOCK_WAIT_SECONDS = 5;
    /** 分布式锁持有时间 */
    private static final long LOCK_LEASE_SECONDS = 600; // 10 min，覆盖整个生成窗口

    /** 单批次处理上限 */
    @Value("${match.d1.batch_size:500}")
    private int batchSize;

    /** 昨天划卡窗口（秒） */
    @Value("${match.d1.active_window_seconds:86400}")
    private int activeWindowSeconds;

    // PRD: 每日 UTC 07:00（美东 EDT 03:00）
    @Scheduled(cron = "0 0 7 * * *", zone = "UTC")
    public void runDailyQueueGen() {
        RLock lock = redissonClient.getLock(CacheKeys.D1_SCHEDULER_LOCK);
        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                log.warn("D1 日更: 未获取到分布式锁，可能已有实例在执行");
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("D1 日更: 获取锁被中断", e);
            return;
        }

        try {
            doGenerate();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doGenerate() {
        log.info("D1 日更队列生成开始");

        Instant since = Instant.now().minusSeconds(activeWindowSeconds);
        Long cursor = null;
        int totalSuccess = 0, totalSkipped = 0, totalBatches = 0;

        // 游标分页：避免一次扫全量
        while (true) {
            List<Long> batch = swipeHistoryManager.listDistinctUserIdsSince(since, batchSize, cursor);
            if (batch.isEmpty()) break;

            totalBatches++;
            Long lastId = null;
            for (Long userId : batch) {
                try {
                    boolean generated = d1Generator.generateForUser(userId);
                    if (generated) totalSuccess++;
                    else totalSkipped++;
                } catch (Exception e) {
                    log.error("D1 生成失败: userId={}", userId, e);
                    totalSkipped++;
                }
                lastId = userId;
            }

            // 批次不满说明到尾了
            if (batch.size() < batchSize) break;
            cursor = lastId;
        }

        log.info("D1 日更队列生成完成: 总批次={}, 成功={}, 跳过={}",
                totalBatches, totalSuccess, totalSkipped);
    }
}
