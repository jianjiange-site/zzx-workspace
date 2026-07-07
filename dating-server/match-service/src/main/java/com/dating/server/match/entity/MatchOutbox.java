package com.dating.server.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("match_outbox")
public class MatchOutbox {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long matchId;
    private String action;
    private String payloadJson;
    private Integer attempts;
    private Instant nextRetryAt;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;
}
