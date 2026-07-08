package com.dating.server.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("coin_ledger")
public class CoinLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private Long amount;
    private Long balanceAfter;
    private Long paidAmount;
    private Long paidBalanceAfter;
    private String reason;
    private String extra;
    private String idempotencyKey;
    private Instant createdAt;
}
