package com.dating.server.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("like_record")
public class LikeRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private Integer fromUserType;
    private Integer source;
    private String likeContent;
    private Instant likedAt;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
    @TableLogic
    private Boolean deleted;
}
