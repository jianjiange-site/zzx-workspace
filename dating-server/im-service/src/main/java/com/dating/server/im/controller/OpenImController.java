package com.dating.server.im.controller;

import com.dating.server.im.config.OpenIMProperties;
import com.dating.server.im.service.OpenImService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OpenIM 相关 HTTP 端点
 *
 * 为 App / mobile-gateway 提供：
 *   - GET /api/v1/openim/config — 获取 OpenIM SDK 连接配置
 *   - POST /api/v1/openim/token — 获取当前用户的 OpenIM Token
 *
 * 原理：App 端 OpenIM SDK 需要 apiAddr + wsAddr + token 才能连接。
 * 服务器签发了 token 后，App 用 SDK login() 接入 IM。
 */
@RestController
@RequestMapping("/api/v1/openim")
@RequiredArgsConstructor
public class OpenImController {

    private final OpenImService openImService;
    private final OpenIMProperties openIMProperties;

    /**
     * 获取 OpenIM SDK 连接配置
     *
     * 返回 SDK 初始化需要的 apiAddr 和 wsAddr（不含 token，token 单独获取）。
     * App 端先调这个拿到地址，再调 /token 拿到 token，然后初始化 SDK。
     */
    @GetMapping("/config")
    public ConfigResponse getConfig() {
        return new ConfigResponse(
                openIMProperties.getApiUrl(),
                openIMProperties.getWsUrl());
    }

    /**
     * 获取用户的 OpenIM Token
     *
     * Demo 直接从请求体取 userId（简化鉴权）。
     * 生产环境通过 mobile-gateway 转发，由 JWT 过滤器设置用户身份。
     *
     * 返回 token + SDK 连接信息，App 端用 userId + token 调 SDK login()。
     */
    @PostMapping("/token")
    public TokenResponse getToken(@RequestBody Map<String, Object> body) {
        Object uid = body.get("userId");
        if (uid == null) {
            throw new IllegalArgumentException("缺少 userId");
        }
        Long userId = uid instanceof Number n ? n.longValue() : Long.parseLong(uid.toString());

        String token = openImService.getOrCreateToken(userId);
        return new TokenResponse(
                String.valueOf(userId),
                token,
                openIMProperties.getApiUrl(),
                openIMProperties.getWsUrl());
    }

    record ConfigResponse(String apiUrl, String wsUrl) {}

    record TokenResponse(String userId, String token, String apiUrl, String wsUrl) {}
}
