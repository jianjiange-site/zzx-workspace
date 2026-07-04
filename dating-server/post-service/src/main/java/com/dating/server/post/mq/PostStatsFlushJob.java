package com.dating.server.post.mq;

import com.dating.server.post.constant.CacheKeys;
import com.dating.server.post.mapper.PostStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * 写合并刷新任务：每 60s 把 Redis 中的点赞/评论增量刷到 post_stats 表
 *
 * 为什么需要这个？
 *   点赞和评论是高频操作，直接写 DB 压力大。
 *   Redis INCR 做缓冲，定时批量刷盘，减少 DB 写入次数。
 *
 * 流程：
 *   1. 读 Redis Set 获取"有未刷增量"的帖子 ID
 *   2. GETDEL 原子取走计数器值
 *   3. UPDATE post_stats SET count = count + delta WHERE post_id = ?
 *   4. 从 Redis Set 移除已处理的帖子
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostStatsFlushJob {

    private final StringRedisTemplate stringRedisTemplate;
    private final PostStatsMapper postStatsMapper;

    /** Redis 脏集合 key：待刷盘的点赞帖子 */
    private static final String DIRTY_LIKE_KEY = "post:dirty:like";
    /** Redis 脏集合 key：待刷盘的评论帖子 */
    private static final String DIRTY_COMMENT_KEY = "post:dirty:comment";

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "flushLikeCounts", lockAtLeastFor = "50s", lockAtMostFor = "55s")
    public void flushLikeCounts() {
        flushCounters(DIRTY_LIKE_KEY, CacheKeys.LIKE_COUNT_PREFIX, "点赞");
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "flushCommentCounts", lockAtLeastFor = "50s", lockAtMostFor = "55s")
    public void flushCommentCounts() {
        flushCounters(DIRTY_COMMENT_KEY, CacheKeys.COMMENT_COUNT_PREFIX, "评论");
    }

    private void flushCounters(String dirtyKey, String countPrefix, String label) {
        Set<String> dirtyPostIds = stringRedisTemplate.opsForSet().members(dirtyKey);
        if (dirtyPostIds == null || dirtyPostIds.isEmpty()) {
            return;
        }

        int flushed = 0;
        for (String postIdStr : dirtyPostIds) {
            try {
                Long postId = Long.parseLong(postIdStr);
                String countKey = countPrefix + postId;

                // GETDEL：原子取走计数器并归零
                String deltaStr = stringRedisTemplate.opsForValue().getAndDelete(countKey);
                if (deltaStr == null) {
                    stringRedisTemplate.opsForSet().remove(dirtyKey, postIdStr);
                    continue;
                }

                int delta = Integer.parseInt(deltaStr);
                if (delta == 0) {
                    stringRedisTemplate.opsForSet().remove(dirtyKey, postIdStr);
                    continue;
                }

                // 增量刷到 post_stats 表
                int affected;
                if (DIRTY_LIKE_KEY.equals(dirtyKey)) {
                    affected = postStatsMapper.incrementLikeCount(postId, delta);
                } else {
                    affected = postStatsMapper.incrementCommentCount(postId, delta);
                }
                if (affected > 0) {
                    flushed++;
                }

                stringRedisTemplate.opsForSet().remove(dirtyKey, postIdStr);
            } catch (Exception e) {
                log.error("刷{}失败: postId={}", label, postIdStr, e);
            }
        }

        if (flushed > 0) {
            log.info("写合并完成: {} 刷了 {} 个帖子", label, flushed);
        }
    }
}
