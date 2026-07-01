package com.dating.server.user.exception;

/**
 * 错误码常量定义
 * user-service 独占 10001-10499 段，与 mobile-gateway 互不重叠
 * 设计文档 §5.8 错误码约定
 */
public class ErrorCodes {

    private ErrorCodes() {}

    // ========== 100xx 用户相关 ==========
    public static final int USER_NOT_FOUND = 10001;
    public static final int USER_BANNED = 10002;
    public static final int USER_SUSPENDED = 10003;
    public static final int OPERATIONAL_BANNED = 10004;

    // ========== 101xx 头像 ==========
    public static final int AVATAR_EXTENSION_NOT_ALLOWED = 10101;
    public static final int AVATAR_SIZE_EXCEEDED = 10102;
    public static final int AVATAR_UPLOAD_NOT_FOUND = 10103;

    // ========== 102xx 兴趣 ==========
    public static final int INTEREST_IMAGE_LIMIT_EXCEEDED = 10201;
    public static final int INTEREST_TEXT_LIMIT_EXCEEDED = 10202;

    // ========== 103xx 身份解析 ==========
    public static final int PHONE_INVALID = 10301;
    public static final int PHONE_ALREADY_BOUND = 10302;

    // ========== 104xx 批量 ==========
    public static final int BATCH_SIZE_EXCEEDED = 10401;
}
