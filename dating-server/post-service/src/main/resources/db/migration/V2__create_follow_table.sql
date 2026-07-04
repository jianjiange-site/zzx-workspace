-- ============================================================
-- post-service Flyway V2: 用户关注关系表
-- 用于写扩散：发帖后推送给关注者的 timeline
-- ============================================================

CREATE TABLE IF NOT EXISTS user_follow (
    id          BIGSERIAL       PRIMARY KEY,
    follower_id BIGINT          NOT NULL,  -- 关注者
    followee_id BIGINT          NOT NULL,  -- 被关注者
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (follower_id, followee_id)
);
CREATE INDEX idx_user_follow_follower ON user_follow (follower_id);
CREATE INDEX idx_user_follow_followee ON user_follow (followee_id);
