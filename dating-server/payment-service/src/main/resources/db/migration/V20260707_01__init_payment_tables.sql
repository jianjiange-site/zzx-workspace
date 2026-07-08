-- coin_accounts: 金币账户，每用户一行 + 乐观锁
CREATE TABLE coin_accounts (
    user_id       BIGINT PRIMARY KEY,
    balance       BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    paid_balance  BIGINT NOT NULL DEFAULT 0 CHECK (paid_balance >= 0),
    version       INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- coin_ledger: 金币流水（append-only 审计）
CREATE TABLE coin_ledger (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    type               VARCHAR(20) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount             BIGINT NOT NULL,
    balance_after      BIGINT NOT NULL,
    paid_amount        BIGINT NOT NULL DEFAULT 0,
    paid_balance_after BIGINT NOT NULL DEFAULT 0,
    reason             VARCHAR(255) NOT NULL DEFAULT '',
    extra              JSONB,
    idempotency_key    VARCHAR(64),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_user_time ON coin_ledger (user_id, created_at DESC);
-- 幂等键部分唯一索引：NULL 值不进入索引，不参与唯一约束
CREATE UNIQUE INDEX idx_ledger_idempotency ON coin_ledger (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- user_subscription: 订阅档位
CREATE TABLE user_subscription (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    tier        SMALLINT NOT NULL DEFAULT 1,  -- 1=FREE 2=WEEKLY 3=MONTHLY 4=YEARLY
    expires_at  TIMESTAMPTZ,
    source      VARCHAR(20) NOT NULL DEFAULT 'TEST',
    deleted     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_subscription_user ON user_subscription (user_id) WHERE NOT deleted;
