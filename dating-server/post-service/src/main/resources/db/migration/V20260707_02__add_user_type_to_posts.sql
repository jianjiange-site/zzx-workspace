-- 帖子添加用户类型字段，支持 Feed 性别分桶
ALTER TABLE posts ADD COLUMN user_type SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN posts.user_type IS '1=BH 2=DH 0=未知(向后兼容)';
