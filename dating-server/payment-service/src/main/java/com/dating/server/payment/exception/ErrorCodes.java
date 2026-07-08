package com.dating.server.payment.exception;

public interface ErrorCodes {
    int COIN_INSUFFICIENT = 3001;
    int SUBSCRIPTION_NOT_FOUND = 3002;
    int IDEMPOTENCY_CONFLICT = 3003;
    int ACCOUNT_NOT_FOUND = 3004;
    int OPTIMISTIC_LOCK_CONFLICT = 3005;
}
