package com.dating.server.match.exception;

public interface ErrorCodes {
    int USER_NOT_FOUND = 10001;
    int BATCH_SIZE_EXCEEDED = 10401;

    // match-service 独占 20000+
    int INVALID_DIRECTION = 20000;
    int CONCURRENT_SWIPE = 20001;
    int QUOTA_RIGHT_SWIPE_EXCEEDED = 20002;
    int QUOTA_CARD_EXCEEDED = 20003;
    int QUOTA_SUPER_HI_EXCEEDED = 20004;
    int INSUFFICIENT_COINS = 20005;
    int TARGET_NOT_FOUND = 20006;
    int SWIPE_SELF = 20007;
}
