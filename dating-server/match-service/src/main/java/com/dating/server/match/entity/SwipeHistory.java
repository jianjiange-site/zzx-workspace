package com.dating.server.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("user_swipe_history")
public class SwipeHistory {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long targetUserId;
    private Integer targetUserType;
    private Integer direction;
    private Instant swipedAt;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
    @TableLogic
    private Boolean deleted;
}
