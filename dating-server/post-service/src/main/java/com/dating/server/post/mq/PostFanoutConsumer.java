package com.dating.server.post.mq;

import com.dating.server.post.constant.CacheKeys;
import com.dating.server.post.manager.UserFollowManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 写扩散消费者：消费 RocketMQ 消息，把新帖子推送给每个粉丝的 Redis 时间线
 *
 * 流程：
 *   收到消息 → 查作者的粉丝列表 → ZADD 到每个粉丝的 timeline ZSet → 裁剪到 100 条 → 设 7 天 TTL
 *
 * timeline 用 ZSet 存储：score=发布时间戳，member=postId
 * 推荐 Feed 读取时：ZREVRANGE timeline 按时间倒序取
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "zzx-dating-dev-post-fanout-v1",
        consumerGroup = "zzx-dating-dev-post-fanout-consumer",
        accessKey = "${rocketmq.consumer.access-key:}",
        secretKey = "${rocketmq.consumer.secret-key:}"
)
public class PostFanoutConsumer implements RocketMQListener<String> {

    private final UserFollowManager userFollowManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 每个用户时间线最多保留 100 条帖子 */
    private static final long TIMELINE_MAX_SIZE = 100;
    /** 时间线 TTL：7 天无人访问自动过期 */
    private static final long TIMELINE_TTL_DAYS = 7;

    @Override
    public void onMessage(String message) {
        try {
            // 反序列化消息：{ postId, userId, createdAt }
            Map<String, Object> msg = objectMapper.readValue(message,
                    new TypeReference<Map<String, Object>>() {});
            Long postId = Long.valueOf(msg.get("postId").toString());
            Long authorId = Long.valueOf(msg.get("userId").toString());
            long createdAt = Long.parseLong(msg.get("createdAt").toString());

            log.info("写扩散消费: postId={}, authorId={}", postId, authorId);

            // === 第1步：查作者的粉丝列表 ===
            List<Long> followerIds = userFollowManager.getFollowerIds(authorId);
            if (followerIds.isEmpty()) {
                log.debug("没有粉丝，跳过写扩散: postId={}", postId);
                return;
            }

            // === 第2步：推送到每个粉丝的时间线 ===
            for (Long followerId : followerIds) {
                String timelineKey = CacheKeys.timeline(followerId);
                String member = String.valueOf(postId);

                // ZADD：添加帖子到时间线，score=发布时间戳（用于排序）
                stringRedisTemplate.opsForZSet().add(timelineKey, member, createdAt);

                // 裁剪：只保留最近的 100 条
                Long total = stringRedisTemplate.opsForZSet().zCard(timelineKey);
                if (total != null && total > TIMELINE_MAX_SIZE) {
                    // 删除最旧的 (total - 100) 条（按 score 升序，前 N 条为最旧）
                    stringRedisTemplate.opsForZSet().removeRange(
                            timelineKey, 0L, total - TIMELINE_MAX_SIZE - 1);
                }

                // EXPIRE：刷新 TTL，7 天不活跃自动删除
                stringRedisTemplate.expire(timelineKey, TIMELINE_TTL_DAYS, TimeUnit.DAYS);
            }

            log.info("写扩散完成: postId={}, 推送粉丝数={}", postId, followerIds.size());
        } catch (Exception e) {
            // 消费失败记录日志，RocketMQ 会自动重试
            log.error("写扩散消费失败: message={}", message, e);
        }
    }
}
