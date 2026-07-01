package com.dating.server.post.manager;

import com.dating.server.post.entity.PostStats;
import com.dating.server.post.mapper.PostStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostStatsManager {

    private final PostStatsMapper postStatsMapper;

    public PostStats getByPostId(Long postId) {
        return postStatsMapper.selectById(postId);
    }

    public void insert(PostStats stats) {
        postStatsMapper.insert(stats);
    }

    public void updateById(PostStats stats) {
        postStatsMapper.updateById(stats);
    }

    /** 原子增减点赞数 */
    public int incrementLikeCount(Long postId, int delta) {
        PostStats stats = postStatsMapper.selectById(postId);
        if (stats == null) return 0;
        stats.setLikeCount(stats.getLikeCount() + delta);
        postStatsMapper.updateById(stats);
        return stats.getLikeCount();
    }

    /** 原子增减评论数 */
    public int incrementCommentCount(Long postId, int delta) {
        PostStats stats = postStatsMapper.selectById(postId);
        if (stats == null) return 0;
        stats.setCommentCount(stats.getCommentCount() + delta);
        postStatsMapper.updateById(stats);
        return stats.getCommentCount();
    }

    /** 更新 feed 排序分数 */
    public void updateScore(Long postId, double score) {
        PostStats stats = postStatsMapper.selectById(postId);
        if (stats == null) return;
        stats.setScore(score);
        postStatsMapper.updateById(stats);
    }
}
