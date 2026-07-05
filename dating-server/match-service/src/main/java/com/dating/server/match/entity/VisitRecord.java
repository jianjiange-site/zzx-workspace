package com.dating.server.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("visit_record")
public class VisitRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private Integer fromUserType;
    private Integer source;
    private Integer visitCount;
    private Instant visitedAt;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
    @TableLogic
    private Boolean deleted;
}
