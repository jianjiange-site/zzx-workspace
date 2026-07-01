package com.dating.server.user.exception;

/**
 * 业务异常基类
 * 含错误码 code，由 GrpcExceptionAdvice 统一转为 gRPC StatusRuntimeException
 * 设计文档 §5.8：code 10001-10499 归属 user-service
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
