package com.dating.server.post.manager;

import com.dating.server.post.constant.CacheKeys;
import com.dating.server.post.entity.PostStats;
import com.dating.server.post.mapper.PostStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 帖子统计 Manager
 *
 * 写路径（高频）：
 *   点赞/评论 → Redis INCR 计数器 + 标记脏集合
 *   定时任务 PostStatsFlushJob 每 60s 刷到 DB
 *
 * 读路径：
 *   DB 基准值 + Redis 待刷增量（写 coalescing）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostStatsManager {

    private final PostStatsMapper postStatsMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public PostStats getByPostId(Long postId) {
        return postStatsMapper.selectById(postId);
    }

    public void insert(PostStats stats) {
        postStatsMapper.insert(stats);
    }

    public void updateById(PostStats stats) {
        postStatsMapper.updateById(stats);
    }

    /**
     * 原子增减点赞数（写 coalescing）
     * 不直接写 DB，改为 Redis INCR + 脏标记，由定时任务刷盘
     */
    public void incrementLikeCount(Long postId, int delta) {
        String key = CacheKeys.likeCount(postId);
        stringRedisTemplate.opsForValue().increment(key, delta);
        stringRedisTemplate.opsForSet().add(CacheKeys.DIRTY_LIKE_KEY, String.valueOf(postId));
    }

    /**
     * 原子增减评论数（写 coalescing）
     */
    public void incrementCommentCount(Long postId, int delta) {
        String key = CacheKeys.commentCount(postId);
        stringRedisTemplate.opsForValue().increment(key, delta);
        stringRedisTemplate.opsForSet().add(CacheKeys.DIRTY_COMMENT_KEY, String.valueOf(postId));
    }

    /** 获取待刷的点赞增量（读路径用：DB + Redis） */
    public int getPendingLikeCount(Long postId) {
        String val = stringRedisTemplate.opsForValue().get(CacheKeys.likeCount(postId));
        return val != null ? Integer.parseInt(val) : 0;
    }

    /** 获取待刷的评论增量（读路径用：DB + Redis） */
    public int getPendingCommentCount(Long postId) {
        String val = stringRedisTemplate.opsForValue().get(CacheKeys.commentCount(postId));
        return val != null ? Integer.parseInt(val) : 0;
    }

    /** 更新 feed 排序分数 */
    public void updateScore(Long postId, double score) {
        PostStats stats = postStatsMapper.selectById(postId);
        if (stats == null) return;
        stats.setScore(score);
        postStatsMapper.updateById(stats);
    }
}
