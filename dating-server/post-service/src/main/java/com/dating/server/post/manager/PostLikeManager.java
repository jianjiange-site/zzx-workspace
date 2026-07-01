package com.dating.server.post.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.post.constant.CacheKeys;
import com.dating.server.post.entity.PostLike;
import com.dating.server.post.mapper.PostLikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostLikeManager {

    private final PostLikeMapper postLikeMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final long LIKE_CACHE_TTL_HOURS = 24;

    /** 查询点赞记录 */
    public PostLike getByPostIdAndUserId(Long postId, Long userId) {
        return postLikeMapper.selectOne(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getUserId, userId));
    }

    /** 检查是否已点赞（优先走缓存） */
    public boolean isLiked(Long postId, Long userId) {
        String cacheKey = CacheKeys.likeStatus(postId, userId);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return "1".equals(cached);
        }
        boolean liked = postLikeMapper.selectCount(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getUserId, userId)) > 0;
        stringRedisTemplate.opsForValue().set(cacheKey, liked ? "1" : "0",
                LIKE_CACHE_TTL_HOURS, TimeUnit.HOURS);
        return liked;
    }

    /** 点赞数 */
    public long countByPostId(Long postId) {
        return postLikeMapper.selectCount(
                new LambdaQueryWrapper<PostLike>().eq(PostLike::getPostId, postId));
    }

    /** 插入点赞 */
    public void insert(PostLike like) {
        postLikeMapper.insert(like);
        evictLikeCache(like.getPostId(), like.getUserId());
    }

    /** 删除点赞 */
    public void delete(PostLike like) {
        postLikeMapper.deleteById(like.getId());
        evictLikeCache(like.getPostId(), like.getUserId());
    }

    private void evictLikeCache(Long postId, Long userId) {
        stringRedisTemplate.delete(CacheKeys.likeStatus(postId, userId));
        stringRedisTemplate.delete(CacheKeys.likeCount(postId));
    }
}
