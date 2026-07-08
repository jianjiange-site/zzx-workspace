package com.dating.server.post.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.post.entity.Post;
import com.dating.server.post.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Feed 热门池打分任务：每 5 分钟全量重建热门池
 *
 * 算法：Hacker News 热度分
 *   score = likeCount / (hoursSinceCreated + 2)^1.5
 *
 *   - 帖子越新得分越高（除数小）
 *   - 点赞越多得分越高（分子大）
 *   - 2 小时内的新帖得分优势明显，之后快速衰减
 *
 * 流程：
 *   1. 捞近 3 天的帖子
 *   2. 内存算 Hacker News 分数
 *   3. ZADD 到临时 key
 *   4. RENAME 原子切换
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedScoreJob {

    private final PostMapper postMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 热门池最终 key */
    private static final String HOT_POOL_KEY = "post:feed:hot";
    /** 临时 key（写入时用，RENAME 切换到正式 key） */
    private static final String HOT_POOL_TEMP_KEY = "post:feed:hot:temp";
    /** 性别分桶 hot pool 临时 key 模板 */
    private static final String HOT_POOL_GENDER_TEMP = "post:feed:hot:%d:temp";
    /** 性别分桶 hot pool 正式 key 模板 */
    private static final String HOT_POOL_GENDER_KEY = "post:feed:hot:%d";
    /** 捞帖窗口：3 天内的帖子参与打分 */
    private static final int RECENT_DAYS = 3;
    /** 热门池最多保留帖子数 */
    private static final long POOL_MAX_SIZE = 1000;

    @Scheduled(fixedDelay = 5 * 60 * 1000) // 5 分钟
    @SchedulerLock(name = "feedScoreJob", lockAtLeastFor = "4m", lockAtMostFor = "4m30s")
    public void rebuildHotPool() {
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(RECENT_DAYS));

        // 第1步：捞近 3 天的已发布帖子
        List<Post> recentPosts = postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getStatus, "PUBLISHED")
                        .ge(Post::getCreatedAt, since)
                        .orderByDesc(Post::getCreatedAt));

        if (recentPosts.isEmpty()) {
            log.debug("近3天没有帖子，跳过热门池重建");
            return;
        }

        // 第2步：内存算分 + 写入主池和性别分桶
        int scored = 0;
        // 用于性别分桶的临时 ZSet
        stringRedisTemplate.delete(HOT_POOL_TEMP_KEY);
        stringRedisTemplate.delete(String.format(HOT_POOL_GENDER_TEMP, 1));
        stringRedisTemplate.delete(String.format(HOT_POOL_GENDER_TEMP, 2));

        for (Post post : recentPosts) {
            long hoursSinceCreated = Duration.between(post.getCreatedAt(), now).toHours();
            double score = hackerNewsScore(
                    Optional.ofNullable(post.getLikeCount()).orElse(0),
                    hoursSinceCreated);

            // 写入主池
            stringRedisTemplate.opsForZSet()
                    .add(HOT_POOL_TEMP_KEY, String.valueOf(post.getId()), score);

            // 写入性别分桶
            int ut = post.getUserType() != null ? post.getUserType() : 0;
            if (ut == 1 || ut == 2) {
                stringRedisTemplate.opsForZSet()
                        .add(String.format(HOT_POOL_GENDER_TEMP, ut), String.valueOf(post.getId()), score);
            }

            // 更新 posts 表的 score 字段
            if (Math.abs((post.getScore() != null ? post.getScore() : 0.0) - score) > 0.001) {
                Post update = new Post();
                update.setId(post.getId());
                update.setScore(score);
                postMapper.updateById(update);
            }

            scored++;
        }

        // 裁剪并原子切换
        trimAndSwap(HOT_POOL_TEMP_KEY, HOT_POOL_KEY);
        trimAndSwap(String.format(HOT_POOL_GENDER_TEMP, 1), String.format(HOT_POOL_GENDER_KEY, 1));
        trimAndSwap(String.format(HOT_POOL_GENDER_TEMP, 2), String.format(HOT_POOL_GENDER_KEY, 2));

        log.info("热门池重建完成: 计算了 {} 个帖子, 主池保留 {} 个", scored,
                Math.min(scored, POOL_MAX_SIZE));
    }

    /** 裁剪 ZSet 到上限，RENAME 原子切换 */
    private void trimAndSwap(String tempKey, String targetKey) {
        Long total = stringRedisTemplate.opsForZSet().zCard(tempKey);
        if (total != null && total > POOL_MAX_SIZE) {
            stringRedisTemplate.opsForZSet().removeRange(tempKey, 0, total - POOL_MAX_SIZE - 1);
        }
        stringRedisTemplate.delete(targetKey);
        stringRedisTemplate.rename(tempKey, targetKey);
    }

    /**
     * Hacker News 热度算法
     * score = likeCount / (hoursSinceCreate + 2)^1.5
     */
    static double hackerNewsScore(int likes, long hoursSinceCreated) {
        double ageHours = Math.max(hoursSinceCreated, 0) + 2;
        return likes / Math.pow(ageHours, 1.5);
    }
}
