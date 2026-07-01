package com.dating.server.post.exception;

/**
 * 业务异常基类
 * code 20001-20499 归属 post-service
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
