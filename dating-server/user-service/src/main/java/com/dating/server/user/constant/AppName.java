package com.dating.server.user.constant;

import lombok.Getter;

/**
 * 应用标识枚举
 * 用于 app_name 字段，区分同一服务下不同 App 的数据隔离
 * 当前仅有 zzx-dating，后续可扩展
 */
@Getter
public enum AppName {

    ZZX_DATING("zzx-dating");

    private final String value;

    AppName(String value) {
        this.value = value;
    }
}
