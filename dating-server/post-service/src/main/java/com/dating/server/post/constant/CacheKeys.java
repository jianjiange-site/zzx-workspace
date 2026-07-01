package com.dating.server.post.constant;

/**
 * Redis 缓存 key 生成工具
 * 前缀固定 "post:"，符合 CLAUDE.md 规范
 */
public class CacheKeys {

    private static final String PREFIX = "post:";

    /** 帖子详情缓存，Hash 类型，TTL 3h */
    public static String post(Long postId) {
        return PREFIX + "detail:" + postId;
    }

    /** 帖子图片列表缓存，String(JSON)，TTL 3h */
    public static String postImages(Long postId) {
        return PREFIX + "images:" + postId;
    }

    /** 用户时间线缓存（自己的 Feed 列表），ZSet 类型，score=发布时间戳，TTL 7d */
    public static String timeline(Long userId) {
        return PREFIX + "timeline:" + userId;
    }

    /** 点赞状态缓存：防止重复点赞，String("1"/null)，TTL 24h */
    public static String likeStatus(Long postId, Long userId) {
        return PREFIX + "like:" + postId + ":" + userId;
    }

    /** 帖子点赞数缓存（写 coalescing），TTL 1h */
    public static String likeCount(Long postId) {
        return PREFIX + "count:like:" + postId;
    }

    /** 帖子评论数缓存（写 coalescing），TTL 1h */
    public static String commentCount(Long postId) {
        return PREFIX + "count:comment:" + postId;
    }

    /** 操作锁：发帖分布式锁，TTL 10s */
    public static String createPostLock(Long userId) {
        return "lock:post:create:" + userId;
    }

    /** 操作锁：点赞分布式锁，TTL 5s */
    public static String likeLock(Long postId, Long userId) {
        return "lock:post:like:" + postId + ":" + userId;
    }

    private CacheKeys() {}
}
