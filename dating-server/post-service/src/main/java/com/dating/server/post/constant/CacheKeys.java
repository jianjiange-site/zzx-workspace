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

    /** 热门池 key（FeedScoreJob 每 5 分钟重建） */
    public static final String HOT_POOL_KEY = PREFIX + "feed:hot";

    /** 按性别分桶的热门池 key：userType=1(BH) 2(DH)，0 则不区分 */
    public static String hotPoolByGender(int userType) {
        return userType > 0 ? PREFIX + "feed:hot:" + userType : HOT_POOL_KEY;
    }

    /** 冷启动池 key，按性别分桶 */
    public static String coldPoolByGender(int userType) {
        return userType > 0 ? PREFIX + "feed:cold:" + userType : PREFIX + "feed:cold";
    }

    /** 已读帖子去重 Set，key = post:seen:{userId}，TTL 7d */
    public static String seenSet(Long userId) {
        return PREFIX + "seen:" + userId;
    }

    /** 写 coalescing 计数器前缀 */
    public static final String LIKE_COUNT_PREFIX = PREFIX + "count:like:";
    public static final String COMMENT_COUNT_PREFIX = PREFIX + "count:comment:";

    /** 写 coalescing 脏集合 key */
    public static final String DIRTY_LIKE_KEY = "post:dirty:like";
    public static final String DIRTY_COMMENT_KEY = "post:dirty:comment";

    /** 帖子点赞数缓存（写 coalescing），TTL 1h */
    public static String likeCount(Long postId) {
        return LIKE_COUNT_PREFIX + postId;
    }

    /** 帖子评论数缓存（写 coalescing），TTL 1h */
    public static String commentCount(Long postId) {
        return COMMENT_COUNT_PREFIX + postId;
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
