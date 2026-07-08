package com.dating.server.match.constant;

public class CacheKeys {
    private static final String PREFIX = "match:";

    public static String quota(Long userId, String yyyymmdd) {
        return PREFIX + "quota:" + userId + ":" + yyyymmdd;
    }

    public static String feed(Long userId) {
        return PREFIX + "feed:" + userId;
    }

    public static String swiped(Long userId) {
        return PREFIX + "swiped:" + userId;
    }

    /** swipe 并发锁（Redisson），5s 自动释放 */
    public static String swipeLock(Long userId, Long targetUserId) {
        return PREFIX + "swipe:lock:" + userId + ":" + targetUserId;
    }

    public static String pref(Long userId) {
        return PREFIX + "pref:" + userId;
    }

    public static String cooldown(Long userId) {
        return PREFIX + "dh_plan:cooldown:" + userId;
    }

    public static String lastScene(Long userId) {
        return PREFIX + "dh_plan:last_scene:" + userId;
    }

    public static String cursorOnline() {
        return PREFIX + "dh_plan:cursor:online";
    }

    public static String cursorOffline() {
        return PREFIX + "dh_plan:cursor:offline";
    }

    /** D1 日更调度分布式锁 */
    public static final String D1_SCHEDULER_LOCK = PREFIX + "d1:scheduler:lock";

    private CacheKeys() {}
}
