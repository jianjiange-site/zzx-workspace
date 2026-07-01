CREATE TABLE user_info (
    id                BIGINT PRIMARY KEY,
    app_name          VARCHAR(64)     NOT NULL,
    nickname          VARCHAR(64),
    gender            SMALLINT        NOT NULL DEFAULT 0,
    birthday          DATE,
    age               SMALLINT,
    bio               VARCHAR(500),
    profession        VARCHAR(128),
    education         VARCHAR(128),
    height            SMALLINT,
    preferred_location VARCHAR(128),
    custom_avatar     JSONB,
    pending           BOOLEAN         NOT NULL DEFAULT TRUE,
    regulation_status SMALLINT        NOT NULL DEFAULT 0,
    last_open_at      TIMESTAMPTZ,
    phone_number      VARCHAR(32),
    email             VARCHAR(256),
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE user_info IS '用户主资料';
COMMENT ON COLUMN user_info.app_name IS '应用标识';
COMMENT ON COLUMN user_info.nickname IS '昵称';
COMMENT ON COLUMN user_info.gender IS '性别 0=未知 1=男 2=女';
COMMENT ON COLUMN user_info.birthday IS '生日';
COMMENT ON COLUMN user_info.age IS '年龄';
COMMENT ON COLUMN user_info.bio IS '个人简介';
COMMENT ON COLUMN user_info.profession IS '职业(UI 称 Occupation)';
COMMENT ON COLUMN user_info.education IS '学历';
COMMENT ON COLUMN user_info.height IS '身高(cm)';
COMMENT ON COLUMN user_info.preferred_location IS '偏好位置';
COMMENT ON COLUMN user_info.custom_avatar IS '头像 object_key 结构体 JSONB: {originalKey,minKey,midKey}';
COMMENT ON COLUMN user_info.pending IS '是否为未补齐资料的占位用户';
COMMENT ON COLUMN user_info.regulation_status IS '监管状态 0=正常 2=封禁 5=暂停';
COMMENT ON COLUMN user_info.last_open_at IS '末次打开 App 时间';
COMMENT ON COLUMN user_info.phone_number IS '联系方式-手机号';
COMMENT ON COLUMN user_info.email IS '联系方式-邮箱';

CREATE INDEX idx_user_info_app_name ON user_info (app_name);


CREATE TABLE user_login_phone (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    phone_e164  VARCHAR(32) NOT NULL,
    app_name    VARCHAR(64) NOT NULL,
    verified_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE user_login_phone IS '手机号与用户绑定';
COMMENT ON COLUMN user_login_phone.user_id IS '用户 ID';
COMMENT ON COLUMN user_login_phone.phone_e164 IS 'E.164 格式手机号';
COMMENT ON COLUMN user_login_phone.app_name IS '应用标识';
COMMENT ON COLUMN user_login_phone.verified_at IS '手机号验证时间';

CREATE UNIQUE INDEX uk_phone_app ON user_login_phone (phone_e164, app_name);
CREATE INDEX idx_user_login_phone_user_id ON user_login_phone (user_id);


CREATE TABLE user_third_party_registration (
    id                 BIGINT PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    platform           VARCHAR(32)  NOT NULL,
    third_party_user_id VARCHAR(256) NOT NULL,
    app_name           VARCHAR(64)  NOT NULL,
    google_email       VARCHAR(256),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE user_third_party_registration IS '第三方账号与用户绑定';
COMMENT ON COLUMN user_third_party_registration.user_id IS '用户 ID';
COMMENT ON COLUMN user_third_party_registration.platform IS '第三方平台(google, apple, wechat)';
COMMENT ON COLUMN user_third_party_registration.third_party_user_id IS '第三方用户 ID';
COMMENT ON COLUMN user_third_party_registration.app_name IS '应用标识';
COMMENT ON COLUMN user_third_party_registration.google_email IS 'Google 邮箱(仅 platform=google 时不为空)';

CREATE UNIQUE INDEX uk_platform_third_party ON user_third_party_registration (platform, third_party_user_id);
CREATE INDEX idx_user_third_party_user_id ON user_third_party_registration (user_id);


CREATE TABLE user_device_registration (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    device_id   VARCHAR(256) NOT NULL,
    platform    VARCHAR(32)  NOT NULL,
    app_name    VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE user_device_registration IS '设备与用户绑定(快速登录)';
COMMENT ON COLUMN user_device_registration.user_id IS '用户 ID';
COMMENT ON COLUMN user_device_registration.device_id IS '设备 ID';
COMMENT ON COLUMN user_device_registration.platform IS '设备平台(ios, android)';
COMMENT ON COLUMN user_device_registration.app_name IS '应用标识';

CREATE UNIQUE INDEX uk_device_platform_app ON user_device_registration (device_id, platform, app_name);
CREATE INDEX idx_user_device_user_id ON user_device_registration (user_id);


CREATE TABLE user_interest (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    type        VARCHAR(16)  NOT NULL,
    pic_key     VARCHAR(512),
    content     VARCHAR(128),
    sort_order  SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE user_interest IS '用户兴趣标签';
COMMENT ON COLUMN user_interest.user_id IS '用户 ID';
COMMENT ON COLUMN user_interest.type IS '兴趣类型 IMAGE/TEXT';
COMMENT ON COLUMN user_interest.pic_key IS '图片 object_key(type=IMAGE 时)';
COMMENT ON COLUMN user_interest.content IS '文字内容(type=TEXT 时)';
COMMENT ON COLUMN user_interest.sort_order IS '排序序号';

CREATE INDEX idx_user_interest_user_id ON user_interest (user_id);
