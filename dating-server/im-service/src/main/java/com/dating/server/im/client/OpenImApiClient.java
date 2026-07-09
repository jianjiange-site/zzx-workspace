package com.dating.server.im.client;

import com.dating.server.im.config.OpenIMProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * OpenIM REST API 客户端
 *
 * 对 OpenIM 服务器的 HTTP 调用都收敛在这里：
 *   - 管理员 Token 获取 + 缓存
 *   - 用户注册（懒注册）
 *   - 用户 Token 签发
 *   - 建单聊会话
 *   - 发送消息
 *
 * 原理：用管理员密钥（adminSecret）获取管理员 Token，后续操作附带该 Token。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenImApiClient {

    private final OpenIMProperties properties;
    private final RestTemplate restTemplate;

    // 管理员 Token 缓存
    private String cachedAdminToken;
    private Instant adminTokenExpiresAt;

    @PostConstruct
    public void init() {
        log.info("OpenIM API URL: {}", properties.getApiUrl());
    }

    // ═══════════════════════════════════════
    //  Token 管理
    // ═══════════════════════════════════════

    /**
     * 获取管理员 Token（带缓存）
     *
     * POST /auth/get_admin_token
     * Body:  { "secret": "...", "userID": "imAdmin" }
     * 返回:  { "errCode": 0, "errMsg": "", "chatToken": "xxx", "expiresAt": 123456 }
     */
    public synchronized String getAdminToken() {
        if (cachedAdminToken != null && adminTokenExpiresAt != null
                && Instant.now().isBefore(adminTokenExpiresAt.minusSeconds(60))) {
            return cachedAdminToken;
        }
        try {
            String url = properties.getApiUrl() + "/auth/get_admin_token";
            Map<String, Object> body = Map.of(
                    "secret", properties.getAdminSecret(),
                    "userID", properties.getAdminUserId());

            ResponseEntity<Map> resp = restTemplate.postForEntity(url, body, Map.class);
            Map data = resp.getBody();
            if (data == null || !isSuccess(data)) {
                log.error("获取 OpenIM 管理员 Token 失败: {}", data);
                return null;
            }
            cachedAdminToken = (String) data.get("chatToken");
            Object expiresObj = data.get("expiresAt");
            if (expiresObj instanceof Number n) {
                adminTokenExpiresAt = Instant.ofEpochSecond(n.longValue());
            } else {
                adminTokenExpiresAt = Instant.now().plusSeconds(3600);
            }
            log.debug("OpenIM 管理员 Token 已刷新, 过期: {}", adminTokenExpiresAt);
            return cachedAdminToken;
        } catch (Exception e) {
            log.error("获取 OpenIM 管理员 Token 异常", e);
            return null;
        }
    }

    // ═══════════════════════════════════════
    //  用户管理
    // ═══════════════════════════════════════

    /**
     * 在 OpenIM 注册用户（幂等）
     *
     * POST /user/user_register
     * Header: token: <admin_token>
     * Body:   { "users": [{ "userID": "123", "nickname": "xxx", "faceURL": "" }] }
     */
    public boolean registerUser(Long userId, String nickname, String faceURL) {
        try {
            String url = properties.getApiUrl() + "/user/user_register";
            String token = getAdminToken();
            if (token == null) return false;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("token", token);

            Map<String, Object> user = Map.of(
                    "userID", String.valueOf(userId),
                    "nickname", nickname != null ? nickname : "user_" + userId,
                    "faceURL", faceURL != null ? faceURL : "");
            Map<String, Object> body = Map.of("users", List.of(user));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map data = resp.getBody();

            if (data == null) return false;
            if (isSuccess(data)) {
                log.info("OpenIM 注册用户成功: userId={}, nickname={}", userId, nickname);
                return true;
            }
            // 幂等：用户已存在也算成功
            String errMsg = (String) data.get("errMsg");
            if (errMsg != null && (errMsg.contains("registered") || errMsg.contains("exist"))) {
                log.debug("OpenIM 用户已存在: userId={}", userId);
                return true;
            }
            log.warn("OpenIM 注册用户失败: userId={}, errMsg={}", userId, errMsg);
            return false;
        } catch (Exception e) {
            log.error("OpenIM 注册用户异常: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 获取用户 IM Token
     *
     * POST /auth/get_user_token
     * Body: { "secret": "...", "userID": "123", "platform": 5 }
     * 返回: { "errCode": 0, "errMsg": "", "token": "xxx", "expiresAt": 123456 }
     */
    public String getUserToken(Long userId) {
        try {
            String url = properties.getApiUrl() + "/auth/get_user_token";
            Map<String, Object> body = Map.of(
                    "secret", properties.getAdminSecret(),
                    "userID", String.valueOf(userId),
                    "platform", 5); // 5 = iOS, 1 = Android, 可配置

            ResponseEntity<Map> resp = restTemplate.postForEntity(url, body, Map.class);
            Map data = resp.getBody();
            if (data == null || !isSuccess(data)) {
                log.warn("获取 OpenIM 用户 Token 失败: userId={}, resp={}", userId, data);
                return null;
            }
            String token = (String) data.get("token");
            log.debug("获取 OpenIM 用户 Token 成功: userId={}", userId);
            return token;
        } catch (Exception e) {
            log.error("获取 OpenIM 用户 Token 异常: userId={}", userId, e);
            return null;
        }
    }

    // ═══════════════════════════════════════
    //  会话管理
    // ═══════════════════════════════════════

    /**
     * 创建单聊会话
     *
     * POST /conversation/create_single_conversation
     * Header: token: <admin_token>
     * Body:   { "ownerUserID": "A", "userIDList": ["B"] }
     */
    public String createSingleConversation(Long ownerUserId, Long otherUserId) {
        try {
            String url = properties.getApiUrl() + "/conversation/create_single_conversation";
            String token = getAdminToken();
            if (token == null) return null;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("token", token);

            Map<String, Object> body = Map.of(
                    "ownerUserID", String.valueOf(ownerUserId),
                    "userIDList", List.of(String.valueOf(otherUserId)));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map data = resp.getBody();

            if (data != null && isSuccess(data)) {
                log.info("OpenIM 创建会话成功: owner={}, other={}", ownerUserId, otherUserId);
                return "single_" + ownerUserId + "_" + otherUserId;
            }
            log.warn("OpenIM 创建会话失败: owner={}, other={}, resp={}", ownerUserId, otherUserId, data);
            return null;
        } catch (Exception e) {
            log.error("OpenIM 创建会话异常: owner={}, other={}", ownerUserId, otherUserId, e);
            return null;
        }
    }

    // ═══════════════════════════════════════
    //  消息
    // ═══════════════════════════════════════

    /**
     * 发送消息（Long 型 userId 版本）
     */
    public boolean sendMessage(Long fromUserId, Long toUserId, String content, String contentType) {
        return sendMessageStr(
                String.valueOf(fromUserId),
                String.valueOf(toUserId),
                content, contentType);
    }

    /**
     * 发送消息（字符串 userId 版本）
     *
     * POST /msg/send_msg
     * Header: token: <admin_token>
     * Body:   { "sendID": "...", "recvID": "...", "content": {...}, "senderNickname": "..." }
     */
    public boolean sendMessageStr(String fromUserId, String toUserId, String content, String contentType) {
        try {
            String url = properties.getApiUrl() + "/msg/send_msg";
            String token = getAdminToken();
            if (token == null) return false;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("token", token);

            // OpenIM 消息内容格式：Text 类型用 textElem
            Map<String, Object> textElem = Map.of("content", content);
            Map<String, Object> messageContent = Map.of(
                    "textElem", textElem,
                    "contentType", contentType != null ? contentType : 101); // 101=Text

            Map<String, Object> body = Map.of(
                    "sendID", String.valueOf(fromUserId),
                    "recvID", String.valueOf(toUserId),
                    "content", messageContent,
                    "senderNickname", "System");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map data = resp.getBody();

            if (data != null && isSuccess(data)) {
                log.debug("OpenIM 发送消息成功: from={}, to={}", fromUserId, toUserId);
                return true;
            }
            log.warn("OpenIM 发送消息失败: from={}, to={}, resp={}", fromUserId, toUserId, data);
            return false;
        } catch (Exception e) {
            log.error("OpenIM 发送消息异常: from={}, to={}", fromUserId, toUserId, e);
            return false;
        }
    }

    // ═══════════════════════════════════════
    //  工具
    // ═══════════════════════════════════════

    private boolean isSuccess(Map data) {
        Object code = data.get("errCode");
        return code == null || (code instanceof Number n && n.intValue() == 0);
    }
}
