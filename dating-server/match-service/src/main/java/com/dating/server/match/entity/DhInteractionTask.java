package com.dating.server.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("dh_interaction_task")
public class DhInteractionTask {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private Integer action;
    private Integer scene;
    private Instant executeTime;
    private String likeContent;
    private Instant createdAt;
}
