package com.dating.server.im.service;

import com.dating.server.im.client.OpenImApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * OpenIM 业务逻辑封装
 *
 * 原理：对 OpenIM 的操作（注册、Token、建会话、发消息）先查本地缓存/状态，
 * 没有或者失败再调远端 API。懒注册：用户第一次需要聊天时再注册到 OpenIM。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenImService {

    private final OpenImApiClient openImApiClient;

    /**
     * 确保用户在 OpenIM 已注册（幂等）
     *
     * 如果用户在 OpenIM 不存在，自动注册。注册失败不阻断主流程。
     */
    public boolean ensureUserInOpenIm(Long userId, String nickname) {
        if (userId == null) return false;
        return openImApiClient.registerUser(userId, nickname, null);
    }

    /**
     * 获取用户的 OpenIM Token（带懒注册兜底）
     *
     * 流程：先获取 Token → 失败则注册用户 → 重试获取 Token
     */
    public String getOrCreateToken(Long userId) {
        String token = openImApiClient.getUserToken(userId);
        if (token != null) return token;

        // 令牌获取失败，尝试注册后再拿一次
        log.info("用户 OpenIM Token 获取失败，尝试懒注册: userId={}", userId);
        openImApiClient.registerUser(userId, "user_" + userId, null);
        return openImApiClient.getUserToken(userId);
    }

    /**
     * 为匹配双方创建 OpenIM 会话（双向）
     *
     * 需要分别为 A→B 和 B→A 创建会话，这样双方都能看到聊天入口。
     */
    public String ensureConversation(Long userIdA, Long userIdB) {
        // 先确保双方都已注册
        ensureUserInOpenIm(userIdA, "user_" + userIdA);
        ensureUserInOpenIm(userIdB, "user_" + userIdB);

        // 创建 A→B 会话（owner 视角）
        String convA = openImApiClient.createSingleConversation(userIdA, userIdB);
        // 创建 B→A 会话
        String convB = openImApiClient.createSingleConversation(userIdB, userIdA);

        String conversationId = "single_" + Math.min(userIdA, userIdB) + "_" + Math.max(userIdA, userIdB);
        if (convA != null || convB != null) {
            log.info("OpenIM 会话创建完成: userIdA={}, userIdB={}, conversationId={}",
                    userIdA, userIdB, conversationId);
            return conversationId;
        }
        log.warn("OpenIM 会话创建失败: userIdA={}, userIdB={}", userIdA, userIdB);
        return conversationId; // 返回模拟 ID，不影响主流程
    }

    /**
     * 发送系统消息给用户
     *
     * 使用 OpenIM 的管理员身份发送系统通知。
     * sendID 用 "imAdmin"（OpenIM 内置管理员账号，字符串类型）。
     */
    public boolean sendSystemMessage(Long toUserId, String title, String body) {
        String content = (title != null ? "【" + title + "】" : "") + (body != null ? body : "");
        // 系统消息用管理员账号发送，toUserId 转成字符串传给 OpenIM
        return openImApiClient.sendMessageStr("imAdmin", String.valueOf(toUserId), content, "101");
    }
}
