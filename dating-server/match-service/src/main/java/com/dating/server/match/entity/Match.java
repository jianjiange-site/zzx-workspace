package com.dating.server.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("match")
public class Match {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userIdLow;
    private Long userIdHigh;
    private Instant matchedAt;
    private String source;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;
}
