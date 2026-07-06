package com.dating.server.gateway.common;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 统一 REST 响应体。所有 API 返回都包在 R<T> 里，code=0 表示成功。
 * 前端/App 统一按 code 判断业务结果，不为 0 时弹 message 给用户。
 */
@Data
@Accessors(chain = true)
public class R<T> {
    private int code;
    private String message;
    private T data;

    /** 成功响应，带 body */
    public static <T> R<T> ok(T data) {
        return new R<T>().setCode(0).setMessage("success").setData(data);
    }

    /** 成功响应，无 body（常用于 delete / update 确认） */
    public static <T> R<T> ok() {
        return ok(null);
    }

    /** 业务异常响应，前端根据 code 决定是否弹窗 */
    public static <T> R<T> fail(int code, String message) {
        return new R<T>().setCode(code).setMessage(message);
    }

    /** token 校验失败的快捷方法 */
    public static <T> R<T> unauthorized(String message) {
        return fail(401, message);
    }
}
