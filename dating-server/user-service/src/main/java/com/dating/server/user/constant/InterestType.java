package com.dating.server.user.constant;

/**
 * 兴趣标签类型
 * IMAGE = 图片标签（存 pic_key）
 * TEXT = 文字标签（存 content）
 */
public enum InterestType {

    IMAGE("IMAGE"),
    TEXT("TEXT");

    private final String value;

    InterestType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
