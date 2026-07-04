package com.dating.server.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 用户关注关系表 user_follow 实体
 *
 * follower_id → 关注者（主动关注别人的人）
 * followee_id → 被关注者（被关注的人）
 * 唯一约束 (follower_id, followee_id) 防止重复关注
 */
@Data
@TableName("user_follow")
public class UserFollow {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关注者 ID */
    private Long followerId;

    /** 被关注者 ID */
    private Long followeeId;

    /** 关注时间 */
    private Instant createdAt;
}
