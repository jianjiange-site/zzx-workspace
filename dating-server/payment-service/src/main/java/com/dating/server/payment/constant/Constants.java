package com.dating.server.payment.constant;

public final class Constants {
    private Constants() {}

    // 订阅档位（对齐 payment.proto SubscriptionTier）
    public static final int TIER_FREE = 1;
    public static final int TIER_WEEKLY = 2;
    public static final int TIER_MONTHLY = 3;
    public static final int TIER_YEARLY = 4;

    // 金币流水类型
    public static final String LEDGER_INCOME = "INCOME";
    public static final String LEDGER_EXPENSE = "EXPENSE";

    // 消费原因
    public static final String REASON_SUPER_HI = "SUPER_HI";
    public static final String REASON_CHAT = "CHAT";

    // Super Hi 金币价格
    public static final int SUPER_HI_COIN_PRICE = 100;

    // 订阅时长（天）
    public static final int WEEKLY_DAYS = 7;
    public static final int MONTHLY_DAYS = 30;
    public static final int YEARLY_DAYS = 365;
}
