package com.dating.server.user.constant;

/**
 * Redis 缓存 key 生成工具
 * 前缀全部以 "user:" 开头，符合 CLAUDE.md 规范
 * 设计文档 §5.5 缓存 key 规范表
 */
public class CacheKeys {

    private static final String PREFIX = "user:";

    /** user_info 主字段缓存，Hash 类型，TTL 24h */
    public static String profile(Long userId) {
        return PREFIX + "profile:" + userId;
    }

    /** 大字段缓存（custom_avatar 等），String(JSON) 独立 key 避免影响 Hash 读取性能，TTL 24h */
    public static String profileBig(Long userId) {
        return PREFIX + "profile:big:" + userId;
    }

    /** 兴趣全量缓存，String(JSON)，TTL 7d */
    public static String interest(Long userId) {
        return PREFIX + "interest:" + userId;
    }

    /** 封禁状态短缓存，String，TTL 5m（避免登录高频回源） */
    public static String banStatus(Long userId) {
        return PREFIX + "ban:status:" + userId;
    }

    /** 运营封禁 UserId 集合，Set 结构，由运营后台写入，成员为 userId 字符串 */
    public static String banOperationalSet() {
        return PREFIX + "ban:operational";
    }

    /** 手机号注册分布式锁 key，TTL 30s */
    public static String registerLockPhone(String phoneE164, String appName) {
        return "lock:user:register:phone:" + phoneE164 + ":" + appName;
    }

    /** 第三方注册分布式锁 key */
    public static String registerLockTp(String platform, String thirdPartyUserId) {
        return "lock:user:register:tp:" + platform + ":" + thirdPartyUserId;
    }

    /** 设备注册分布式锁 key */
    public static String registerLockDevice(String platform, String deviceId, String appName) {
        return "lock:user:register:dev:" + platform + ":" + deviceId + ":" + appName;
    }

    private CacheKeys() {}
}
