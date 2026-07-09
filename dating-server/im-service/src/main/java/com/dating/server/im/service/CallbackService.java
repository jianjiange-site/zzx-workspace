package com.dating.server.im.service;

import com.dating.server.im.adaptor.ImProviderAdaptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 回调分发服务
 *
 * 收到 IM 引擎的原始回调后，遍历所有 Adaptor 找到能处理的，
 * 解析事件类型并执行对应逻辑。
 *
 * 扩展方式：新增 IM 引擎时，加一个 ImProviderAdaptor 实现类，
 * Spring 会自动注入到 adaptors 列表中，无需改这里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackService {

    private final List<ImProviderAdaptor> adaptors;

    /**
     * 处理 IM 引擎的原始回调
     *
     * @param provider 引擎标识（如 "openim"）
     * @param payload  原始回调 JSON（已解析为 Map）
     * @return 处理结果码：0=放行/成功，非0=拒绝
     */
    public int handleCallback(String provider, Map<String, Object> payload) {
        ImProviderAdaptor adaptor = findAdaptor(provider);
        if (adaptor == null) {
            log.warn("不支持的 IM provider: {}", provider);
            return 0; // 不认识的 provider 放行，不阻断
        }

        String eventType = adaptor.parseEventType(provider, payload);
        log.info("IM 回调: provider={}, eventType={}", provider, eventType);

        return switch (eventType) {
            case "before_send" -> handleBeforeSend(adaptor, payload);
            case "after_send"  -> handleAfterSend(adaptor, payload);
            case "online"      -> handleOnline(adaptor, payload);
            case "offline"     -> handleOffline(adaptor, payload);
            default -> {
                log.debug("不处理的事件类型: {}", eventType);
                yield 0;
            }
        };
    }

    private int handleBeforeSend(ImProviderAdaptor adaptor, Map<String, Object> payload) {
        Long senderId = adaptor.extractSenderId(payload);
        String content = adaptor.extractContent(payload);

        log.info("before_send: senderId={}, content={}", senderId, content);
        // 返回 0 = 放行（拦截返回非0码，Demo 阶段全部放行）
        return 0;
    }

    private int handleAfterSend(ImProviderAdaptor adaptor, Map<String, Object> payload) {
        Long senderId = adaptor.extractSenderId(payload);
        Long receiverId = adaptor.extractReceiverId(payload);
        String content = adaptor.extractContent(payload);

        log.info("after_send: senderId={}, receiverId={}", senderId, receiverId);
        // Demo 阶段只记录日志，后续可做消息落库 + AI 回复
        return 0;
    }

    private int handleOnline(ImProviderAdaptor adaptor, Map<String, Object> payload) {
        log.info("online callback: {}", payload);
        // Demo 阶段不额外处理（presence 已有心跳机制）
        return 0;
    }

    private int handleOffline(ImProviderAdaptor adaptor, Map<String, Object> payload) {
        log.info("offline callback: {}", payload);
        return 0;
    }

    private ImProviderAdaptor findAdaptor(String provider) {
        return adaptors.stream()
                .filter(a -> a.supports(provider))
                .findFirst()
                .orElse(null);
    }
}
