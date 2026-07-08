package com.dating.server.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("user_subscription")
public class UserSubscription {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer tier;
    private Instant expiresAt;
    private String source;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
}
