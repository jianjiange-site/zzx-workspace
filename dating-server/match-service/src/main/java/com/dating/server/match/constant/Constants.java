package com.dating.server.match.constant;

public final class Constants {
    private Constants() {}

    public static final int SWIPE_LEFT = 1;
    public static final int SWIPE_RIGHT = 2;
    public static final int SWIPE_SUPER_HI = 3;

    public static final int USER_TYPE_BH = 1;
    public static final int USER_TYPE_DH = 2;

    public static final String MATCH_SOURCE_SWIPE = "SWIPE_MATCH";
    public static final String MATCH_SOURCE_SUPER_HI = "SWIPE_SUPER_HI";

    public static final int LIKE_SOURCE_SWIPE = 1;
    public static final int LIKE_SOURCE_DH_ONLINE = 2;
    public static final int LIKE_SOURCE_DH_OFFLINE = 3;
    public static final int LIKE_SOURCE_SUPER_HI = 4;

    public static final int VISIT_SOURCE_PROFILE = 1;
    public static final int VISIT_SOURCE_DH_ONLINE = 2;
    public static final int VISIT_SOURCE_DH_OFFLINE = 3;

    public static final int DH_ACTION_LIKE = 1;
    public static final int DH_ACTION_VISIT = 2;
    public static final int DH_SCENE_ONLINE = 1;
    public static final int DH_SCENE_OFFLINE = 2;
}
