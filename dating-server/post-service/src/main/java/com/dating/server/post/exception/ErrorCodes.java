package com.dating.server.post.exception;

/**
 * 错误码常量定义
 * post-service 独占 20001-20499 段
 */
public class ErrorCodes {

    private ErrorCodes() {}

    // ========== 200xx 帖子 ==========
    public static final int POST_NOT_FOUND = 20001;
    public static final int POST_DELETED = 20002;
    public static final int POST_CONTENT_TOO_LONG = 20003;
    public static final int POST_IMAGE_LIMIT_EXCEEDED = 20004;

    // ========== 201xx 点赞 ==========
    public static final int LIKE_ALREADY = 20101;
    public static final int LIKE_NOT_FOUND = 20102;

    // ========== 202xx 评论 ==========
    public static final int COMMENT_TOO_LONG = 20201;
    public static final int COMMENT_NOT_FOUND = 20202;
    public static final int COMMENT_DELETED = 20203;

    // ========== 203xx 批量 ==========
    public static final int BATCH_SIZE_EXCEEDED = 20301;
}
