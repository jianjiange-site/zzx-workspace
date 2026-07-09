package com.dating.server.im.adaptor;

import java.util.Map;

/**
 * IM 引擎回调解析器的抽象接口
 *
 * 每接入一个 IM 引擎（OpenIM / 腾讯IM...）就加一个实现类，
 * 通过 supports() 判断是否处理该 provider 的回调。
 *
 * 面试点：这种设计让核心业务逻辑与具体 IM 引擎解耦，
 * 换引擎只需要新增一个 Adaptor 实现，不改任何业务代码。
 */
public interface ImProviderAdaptor {

    /** 当前 adaptor 是否处理该 provider 的回调 */
    boolean supports(String provider);

    /**
     * 将 IM 引擎的原始回调 JSON 解析为归一化事件类型
     *
     * @param provider 引擎标识（如 "openim"）
     * @param payload  原始回调 body
     * @return 事件类型字符串（如 "before_send", "after_send", "online", "offline"）
     *         不识别的回调返回 "unknown"
     */
    String parseEventType(String provider, Map<String, Object> payload);

    /**
     * 从回调中提取发送方用户 ID
     */
    Long extractSenderId(Map<String, Object> payload);

    /**
     * 从回调中提取接收方用户 ID
     */
    Long extractReceiverId(Map<String, Object> payload);

    /**
     * 从回调中提取消息内容
     */
    String extractContent(Map<String, Object> payload);
}
