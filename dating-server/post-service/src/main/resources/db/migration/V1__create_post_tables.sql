-- ============================================================
-- post-service Flyway V1: 动态/帖子相关 5 表 + ShedLock 表
-- ============================================================

-- 1. 帖子主表
CREATE TABLE IF NOT EXISTS posts (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    content         TEXT,
    type            VARCHAR(20)     NOT NULL DEFAULT 'TEXT',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PUBLISHED',
    visibility      VARCHAR(20)     NOT NULL DEFAULT 'PUBLIC',
    topic           VARCHAR(100),
    allow_comment   BOOLEAN         NOT NULL DEFAULT TRUE,
    like_count      INT             NOT NULL DEFAULT 0,
    comment_count   INT             NOT NULL DEFAULT 0,
    share_count     INT             NOT NULL DEFAULT 0,
    view_count      INT             NOT NULL DEFAULT 0,
    score           DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_posts_user_id ON posts (user_id);
CREATE INDEX idx_posts_status_created ON posts (status, created_at DESC);
CREATE INDEX idx_posts_score ON posts (score DESC);
CREATE INDEX idx_posts_topic ON posts (topic);

-- 2. 帖子图片表
CREATE TABLE IF NOT EXISTS post_images (
    id          BIGSERIAL       PRIMARY KEY,
    post_id     BIGINT          NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    object_key  VARCHAR(255)    NOT NULL,
    width       INT,
    height      INT,
    sort_order  INT             NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_post_images_post_id ON post_images (post_id);

-- 3. 帖子统计表（写 coalescing 目标表）
CREATE TABLE IF NOT EXISTS post_stats (
    post_id         BIGINT          PRIMARY KEY REFERENCES posts(id) ON DELETE CASCADE,
    like_count      INT             NOT NULL DEFAULT 0,
    comment_count   INT             NOT NULL DEFAULT 0,
    share_count     INT             NOT NULL DEFAULT 0,
    view_count      INT             NOT NULL DEFAULT 0,
    score           DOUBLE PRECISION NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 4. 帖子点赞表
CREATE TABLE IF NOT EXISTS post_likes (
    id          BIGSERIAL       PRIMARY KEY,
    post_id     BIGINT          NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id     BIGINT          NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (post_id, user_id)
);
CREATE INDEX idx_post_likes_post_id ON post_likes (post_id);
CREATE INDEX idx_post_likes_user_id ON post_likes (user_id);

-- 5. 帖子评论表
CREATE TABLE IF NOT EXISTS post_comments (
    id          BIGSERIAL       PRIMARY KEY,
    post_id     BIGINT          NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id     BIGINT          NOT NULL,
    parent_id   BIGINT          REFERENCES post_comments(id) ON DELETE SET NULL,
    content     TEXT            NOT NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'PUBLISHED',
    like_count  INT             NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_post_comments_post_id ON post_comments (post_id);
CREATE INDEX idx_post_comments_user_id ON post_comments (user_id);
CREATE INDEX idx_post_comments_parent_id ON post_comments (parent_id);

-- 6. ShedLock 表（多实例定时任务互斥锁）
CREATE TABLE IF NOT EXISTS shedlock (
    name        VARCHAR(64)     PRIMARY KEY,
    lock_until  TIMESTAMPTZ     NOT NULL,
    locked_at   TIMESTAMPTZ     NOT NULL,
    locked_by   VARCHAR(255)    NOT NULL
);
