package com.dating.server.post.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 写扩散生产者：发帖后通知 RocketMQ，触发推送到粉丝时间线
 *
 * 流程：发帖 → PostServiceImpl → sendPostCreated() → RocketMQ → PostFanoutConsumer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostFanoutProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /** RocketMQ topic，供写扩散用 */
    private static final String TOPIC = "zzx-dating-dev-post-fanout-v1";

    /**
     * 发帖成功后发送写扩散消息
     *
     * @param postId    帖子 ID
     * @param userId    作者 ID
     * @param createdAt 创建时间戳（epoch millis），作为时间线排序依据
     */
    public void sendPostCreated(Long postId, Long userId, long createdAt) {
        try {
            // 序列化为 JSON：{ postId, userId, createdAt }
            String payload = objectMapper.writeValueAsString(Map.of(
                    "postId", postId,
                    "userId", userId,
                    "createdAt", createdAt
            ));

            // syncSend 同步发送，RocketMQ 客户端内置重试
            rocketMQTemplate.syncSend(TOPIC, payload);
            log.info("写扩散消息已发送: postId={}, userId={}", postId, userId);
        } catch (Exception e) {
            // 写扩散失败不影响发帖主流程，只打日志
            log.error("写扩散消息发送失败: postId={}, userId={}", postId, userId, e);
        }
    }
}
