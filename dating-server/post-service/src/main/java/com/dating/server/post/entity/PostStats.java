package com.dating.server.post.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("post_stats")
public class PostStats {

    @TableId
    private Long postId;

    private Integer likeCount;

    private Integer commentCount;

    private Integer shareCount;

    private Integer viewCount;

    private Double score;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
