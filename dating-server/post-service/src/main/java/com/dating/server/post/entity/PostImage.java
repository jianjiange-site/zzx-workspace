package com.dating.server.post.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("post_images")
public class PostImage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long postId;

    private String objectKey;

    private Integer width;

    private Integer height;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
}
