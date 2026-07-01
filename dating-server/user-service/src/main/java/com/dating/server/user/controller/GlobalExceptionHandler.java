package com.dating.server.user.controller;

import com.dating.server.user.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常统一处理：BizException → 400 + {code, message}。
     * 不打印堆栈（业务异常是预期内），只 WARN 日志记录 code/message。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "code", e.getCode(),
                "message", e.getMessage()
        ));
    }

    /**
     * 未预期异常兜底：返回 500 + 通用文案，不暴露堆栈给前端。
     * ERROR 日志含完整堆栈，用于排查。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("未预期异常", e);
        return ResponseEntity.internalServerError().body(Map.of(
                "code", 500,
                "message", "Internal Server Error"
        ));
    }
}
