package com.dating.server.im.adaptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OpenIM 回调解析器
 *
 * 把 OpenIM 的 Webhook 回调 JSON 解析成归一化内部事件。
 *
 * OpenIM 回调类型（callbackCommand）：
 *   - "beforeSendMessage"  → 消息发出前（可拦截）
 *   - "afterSendMessage"   → 消息发出后
 *   - "callbackUserOnlineCommand"  → 用户上线
 *   - "callbackUserOfflineCommand" → 用户下线
 */
@Slf4j
@Component
public class OpenImAdaptor implements ImProviderAdaptor {

    @Override
    public boolean supports(String provider) {
        return "openim".equalsIgnoreCase(provider);
    }

    @Override
    public String parseEventType(String provider, Map<String, Object> payload) {
        if (!supports(provider)) return "unknown";

        Object cmd = payload.get("callbackCommand");
        if (cmd == null) return "unknown";

        return switch (cmd.toString()) {
            case "beforeSendMessage" -> "before_send";
            case "afterSendMessage"  -> "after_send";
            case "callbackUserOnlineCommand"  -> "online";
            case "callbackUserOfflineCommand" -> "offline";
            default -> {
                log.debug("OpenIM 未知回调: {}", cmd);
                yield "unknown";
            }
        };
    }

    @Override
    public Long extractSenderId(Map<String, Object> payload) {
        Object sendId = payload.get("sendID");
        if (sendId instanceof String s) {
            try { return Long.parseLong(s); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    @Override
    public Long extractReceiverId(Map<String, Object> payload) {
        Object recvId = payload.get("recvID");
        if (recvId instanceof String s) {
            try { return Long.parseLong(s); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    @Override
    public String extractContent(Map<String, Object> payload) {
        Object content = payload.get("content");
        if (content instanceof Map<?, ?> m) {
            Object textElem = m.get("textElem");
            if (textElem instanceof Map<?, ?> t) {
                Object text = t.get("content");
                return text != null ? text.toString() : "";
            }
        }
        return "";
    }
}
