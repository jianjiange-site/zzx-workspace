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

    private CacheKeys() {}
}
