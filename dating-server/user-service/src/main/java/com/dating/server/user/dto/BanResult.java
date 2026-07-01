package com.dating.server.user.dto;

import lombok.Getter;

/**
 * 封禁检查结果 DTO
 * 对应 proto BanResult 的 Java 版本
 * 设计文档 §5.7：检查 user_info.regulation_status + Redis 运营封禁集合
 */
@Getter
public class BanResult {

    private final boolean banned;
    private final String reason;
    private final Long bannedAtMs;
    private final String message;

    private BanResult(boolean banned, String reason, Long bannedAtMs, String message) {
        this.banned = banned;
        this.reason = reason;
        this.bannedAtMs = bannedAtMs;
        this.message = message;
    }

    /** 正常（未封禁） */
    public static BanResult ok() {
        return new BanResult(false, null, null, null);
    }

    /** 封禁 */
    public static BanResult banned(String reason, Long bannedAtMs, String message) {
        return new BanResult(true, reason, bannedAtMs, message);
    }
}
