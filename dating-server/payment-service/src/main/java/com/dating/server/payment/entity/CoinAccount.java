package com.dating.server.payment.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("coin_accounts")
public class CoinAccount {
    @TableId
    private Long userId;
    private Long balance;
    private Long paidBalance;
    @Version
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}
