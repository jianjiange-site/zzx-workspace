package com.dating.server.user.constant;

import lombok.Getter;

/**
 * 设备平台枚举
 * 对应 user_device_registration.platform 和 user_third_party_registration.platform
 * 注意：第三方平台（google/apple/wechat）不在此枚举，直接用字符串
 */
@Getter
public enum Platform {

    IOS("ios"),
    ANDROID("android");

    private final String value;

    Platform(String value) {
        this.value = value;
    }
}
