-- =========================================================
-- match-service V1: 核心表
-- 设计文档: docs/match-service-prd-tech.md §7.2
-- =========================================================

-- 划卡历史（高写入）
CREATE TABLE user_swipe_history (
    id               BIGINT PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    target_user_id   BIGINT      NOT NULL,
    target_user_type SMALLINT    NOT NULL,   -- 1=BH, 2=DH
    direction        SMALLINT    NOT NULL,   -- 1=LEFT, 2=RIGHT, 3=SUPER_HI
    swiped_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (user_id, target_user_id)
);
CREATE INDEX idx_swipe_user_time ON user_swipe_history (user_id, swiped_at DESC);
CREATE INDEX idx_swipe_target_dir ON user_swipe_history (target_user_id, direction) WHERE deleted = FALSE;

COMMENT ON TABLE user_swipe_history IS '划卡历史';
COMMENT ON COLUMN user_swipe_history.user_id IS '划卡用户';
COMMENT ON COLUMN user_swipe_history.target_user_id IS '目标用户';
COMMENT ON COLUMN user_swipe_history.target_user_type IS '目标类型 1=BH 2=DH';
COMMENT ON COLUMN user_swipe_history.direction IS '方向 1=LEFT 2=RIGHT 3=SUPER_HI';

-- 匹配关系
CREATE TABLE match (
    id              BIGINT PRIMARY KEY,
    user_id_low     BIGINT      NOT NULL,
    user_id_high    BIGINT      NOT NULL,
    matched_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source          VARCHAR(30) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (user_id_low, user_id_high)
);
CREATE INDEX idx_match_low_time  ON match (user_id_low,  matched_at DESC);
CREATE INDEX idx_match_high_time ON match (user_id_high, matched_at DESC);

COMMENT ON TABLE match IS '匹配关系';
COMMENT ON COLUMN match.user_id_low IS 'min(uid1, uid2)';
COMMENT ON COLUMN match.user_id_high IS 'max(uid1, uid2)';
COMMENT ON COLUMN match.source IS 'SWIPE_MATCH / SWIPE_SUPER_HI';

-- 匹配副作用重试
CREATE TABLE match_outbox (
    id            BIGINT PRIMARY KEY,
    match_id      BIGINT      NOT NULL,
    action        VARCHAR(40) NOT NULL,
    payload_json  JSONB       NOT NULL,
    attempts      INT         NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NOT NULL,
    status        VARCHAR(20) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted       BOOLEAN     NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE match_outbox IS '匹配副作用重试';
COMMENT ON COLUMN match_outbox.action IS 'ENSURE_CONVERSATION / SYSTEM_MSG / DH_OPENING';
COMMENT ON COLUMN match_outbox.status IS 'PENDING / DONE / DEAD';

-- Like 记录（单向未回应）
CREATE TABLE like_record (
    id              BIGINT PRIMARY KEY,
    from_user_id    BIGINT       NOT NULL,
    to_user_id      BIGINT       NOT NULL,
    from_user_type  SMALLINT     NOT NULL,   -- 1=BH 2=DH
    source          SMALLINT     NOT NULL,   -- 1=SWIPE_RIGHT 2=DH_PLAN_ONLINE 3=DH_PLAN_OFFLINE
    like_content    VARCHAR(200),
    liked_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE (from_user_id, to_user_id)
);
CREATE INDEX idx_like_to_user_time ON like_record (to_user_id, liked_at DESC) WHERE deleted = FALSE;
CREATE INDEX idx_like_to_user_type ON like_record (to_user_id, from_user_type, liked_at DESC) WHERE deleted = FALSE;

COMMENT ON TABLE like_record IS 'Like 记录';
COMMENT ON COLUMN like_record.source IS '1=SWIPE_RIGHT 2=DH_PLAN_ONLINE 3=DH_PLAN_OFFLINE';

-- Visit 记录
CREATE TABLE visit_record (
    id              BIGINT PRIMARY KEY,
    from_user_id    BIGINT       NOT NULL,
    to_user_id      BIGINT       NOT NULL,
    from_user_type  SMALLINT     NOT NULL,
    source          SMALLINT     NOT NULL,   -- 1=PROFILE_VIEW 2=DH_PLAN_ONLINE 3=DH_PLAN_OFFLINE
    visit_count     INT          NOT NULL DEFAULT 1,
    visited_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE (from_user_id, to_user_id)
);
CREATE INDEX idx_visit_to_user_time ON visit_record (to_user_id, visited_at DESC) WHERE deleted = FALSE;
CREATE INDEX idx_visit_to_user_type ON visit_record (to_user_id, from_user_type, visited_at DESC) WHERE deleted = FALSE;

COMMENT ON TABLE visit_record IS '访问记录';
COMMENT ON COLUMN visit_record.source IS '1=PROFILE_VIEW 2=DH_PLAN_ONLINE 3=DH_PLAN_OFFLINE';

-- DH 互动任务（短生命周期，执行后硬删）
CREATE TABLE dh_interaction_task (
    id              BIGINT PRIMARY KEY,
    from_user_id    BIGINT       NOT NULL,
    to_user_id      BIGINT       NOT NULL,
    action          SMALLINT     NOT NULL,   -- 1=LIKE 2=VISIT
    scene           SMALLINT     NOT NULL,   -- 1=ONLINE 2=OFFLINE
    execute_time    TIMESTAMPTZ  NOT NULL,
    like_content    VARCHAR(200),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dh_task_execute_time ON dh_interaction_task (execute_time);
CREATE INDEX idx_dh_task_to_user_scene ON dh_interaction_task (to_user_id, scene);

COMMENT ON TABLE dh_interaction_task IS 'DH 模拟互动任务';
COMMENT ON COLUMN dh_interaction_task.action IS '1=LIKE 2=VISIT';
COMMENT ON COLUMN dh_interaction_task.scene IS '1=ONLINE 2=OFFLINE';
