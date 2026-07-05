-- match-service 所需字段：用户类型、颜值分、人种、位置
ALTER TABLE user_info
    ADD COLUMN user_type     SMALLINT     NOT NULL DEFAULT 1,
    ADD COLUMN beauty_score  SMALLINT,
    ADD COLUMN race          VARCHAR(32),
    ADD COLUMN latitude      DOUBLE PRECISION,
    ADD COLUMN longitude     DOUBLE PRECISION;

COMMENT ON COLUMN user_info.user_type     IS '用户类型 1=BH(真人) 2=DH(数字人)';
COMMENT ON COLUMN user_info.beauty_score  IS '颜值分 0-100';
COMMENT ON COLUMN user_info.race          IS '人种';
COMMENT ON COLUMN user_info.latitude      IS '纬度';
COMMENT ON COLUMN user_info.longitude     IS '经度';

CREATE INDEX idx_user_info_user_type_gender_age_beauty
    ON user_info (user_type, gender, age, beauty_score)
    WHERE user_type = 2;
