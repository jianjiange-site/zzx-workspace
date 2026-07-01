package com.dating.server.user.constant;

/**
 * 性别枚举
 * DB SMALLINT 与 proto 取值一致：0=未知 1=男 2=女
 * 设计文档 §5.7：经 GenderMapping 做 null 防护
 */
public enum Gender {

    UNKNOWN(0),
    MALE(1),
    FEMALE(2);

    private final int value;

    Gender(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /** 从 DB 整型值转枚举，null 或无法识别时默认 UNKNOWN */
    public static Gender fromValue(Integer value) {
        if (value == null) return UNKNOWN;
        for (Gender g : values()) {
            if (g.value == value) return g;
        }
        return UNKNOWN;
    }
}
