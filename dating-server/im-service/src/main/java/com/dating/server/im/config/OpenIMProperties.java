package com.dating.server.im.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "openim")
public class OpenIMProperties {
    /** OpenIM REST API 基础地址 */
    private String apiUrl = "https://nexus-mind.chatvibe.me/api";
    /** 管理员用户 ID */
    private String adminUserId = "imAdmin";
    /** 管理员密钥 */
    private String adminSecret = "c49af41e5a17a9818c26fed0bbb6846e36e288d6000fe28f";
    /** SDK 用的 WebSocket 地址 */
    private String wsUrl = "wss://nexus-mind.chatvibe.me/msg_gateway";
}
