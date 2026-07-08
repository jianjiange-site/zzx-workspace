package com.dating.server.post.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 写扩散生产者：发帖后发送 RocketMQ 消息，触发推送到粉丝时间线
 *
 * 流程：发帖 → PostServiceImpl → sendPostCreated() → RocketMQ Topic → PostFanoutConsumer
 *
 * RocketMQ 机制说明：
 * - syncSend 同步发送，等待 Broker 返回确认，确保消息已持久化
 * - 发送失败自动重试 3 次（由 rocketmq.producer.send-message-timeout 控制每次超时）
 * - 生产环境建议异步发送（asyncSend）提升吞吐，当前同步已满足场景
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostFanoutProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /** RocketMQ topic：zzx-dating-dev 前缀隔离，v1 版本号便于后续升级 */
    private static final String TOPIC = "zzx-dating-dev-post-fanout-v1";
    /** 本地重试次数：设计文档要求 3 次，兜底 Broker 抖动 */
    private static final int MAX_RETRIES = 3;
    /** 每次发送超时（毫秒），匹配 application-dev.yml 的 send-message-timeout */
    private static final long SEND_TIMEOUT_MS = 3000;

    /**
     * 发帖成功后发送写扩散消息
     *
     * 失败不抛异常（不阻塞发帖主流程），只打日志。
     * 即使 fanout 失败，帖子在 5 分钟后的热门池重建中仍可获得曝光。
     *
     * @param postId    帖子 ID（雪花 ID）
     * @param userId    作者 ID
     * @param createdAt 创建时间戳（epoch millis），作为粉丝时间线排序依据
     */
    public void sendPostCreated(Long postId, Long userId, long createdAt) {
        // 序列化为 JSON：{ postId, userId, createdAt }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "postId", postId,
                    "userId", userId,
                    "createdAt", createdAt
            ));
        } catch (Exception e) {
            log.error("写扩散消息序列化失败: postId={}", postId, e);
            return;
        }

        // 本地重试：默认 3 次，每次 3s 超时
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                SendResult result = rocketMQTemplate.syncSend(TOPIC, payload, SEND_TIMEOUT_MS);
                if (SendStatus.SEND_OK == result.getSendStatus()) {
                    log.info("写扩散消息已发送: postId={}, userId={}", postId, userId);
                    return;
                }
                log.warn("写扩散发送返回非 OK, 重试 {}/{}: postId={}, status={}",
                        i + 1, MAX_RETRIES, postId, result.getSendStatus());
            } catch (Exception e) {
                if (i < MAX_RETRIES - 1) {
                    log.warn("写扩散发送异常, 重试 {}/{}: postId={}",
                            i + 1, MAX_RETRIES, postId, e);
                } else {
                    // 最后一次重试也失败，只打日志不阻塞发帖
                    log.error("写扩散消息发送失败(已重试{}次): postId={}, userId={}",
                            MAX_RETRIES, postId, userId, e);
                }
            }
        }
    }
}
