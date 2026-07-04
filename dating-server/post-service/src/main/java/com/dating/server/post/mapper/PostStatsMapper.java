package com.dating.server.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dating.server.post.entity.PostStats;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface PostStatsMapper extends BaseMapper<PostStats> {

    /** 原子累加点赞数 */
    @Update("UPDATE post_stats SET like_count = like_count + #{delta} WHERE post_id = #{postId}")
    int incrementLikeCount(@Param("postId") Long postId, @Param("delta") int delta);

    /** 原子累加评论数 */
    @Update("UPDATE post_stats SET comment_count = comment_count + #{delta} WHERE post_id = #{postId}")
    int incrementCommentCount(@Param("postId") Long postId, @Param("delta") int delta);
}
