package com.dating.server.im.controller;

import com.dating.server.im.service.CallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OpenIM 回调接收端点
 *
 * OpenIM 在用户发消息、上下线时会回调此地址。
 * 当前 Demo 阶段全部放行，只记录日志。
 *
 * 生产环境回调链路：
 *   OpenIM Server ──(HTTP)──► mobile-gateway ──(gRPC)──► im-service
 * 简化 Demo：
 *   OpenIM Server ──(HTTP)──► im-service 直接
 *
 * 返回给 OpenIM 的格式：{"errCode": 0, "errMsg": ""}
 * 0 = 放行，非0 = 拒绝/拦截
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/openim/callback")
@RequiredArgsConstructor
public class OpenImCallbackController {

    private final CallbackService callbackService;

    @PostMapping
    public Map<String, Object> handleCallback(
            @RequestHeader(value = "x-callback-cmd", required = false) String callbackCmd,
            @RequestBody Map<String, Object> payload) {

        if (callbackCmd != null) {
            payload.put("callbackCommand", callbackCmd);
        }

        int code = callbackService.handleCallback("openim", payload);
        return Map.of("errCode", code, "errMsg", code == 0 ? "" : "rejected");
    }
}
