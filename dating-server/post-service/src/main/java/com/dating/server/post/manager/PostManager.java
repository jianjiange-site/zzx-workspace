package com.dating.server.post.manager;

import com.dating.server.post.constant.CacheKeys;
import com.dating.server.post.entity.Post;
import com.dating.server.post.entity.PostImage;
import com.dating.server.post.mapper.PostImageMapper;
import com.dating.server.post.mapper.PostMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostManager {

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final long POST_CACHE_TTL_HOURS = 3;

    // ===== 读 =====

    public Post getById(Long id) {
        return postMapper.selectById(id);
    }

    public Post getCachedById(Long id) {
        String cacheKey = CacheKeys.post(id);
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, Post.class);
            } catch (Exception e) {
                log.warn("帖子缓存JSON解析失败: key={}", cacheKey, e);
            }
        }
        Post post = postMapper.selectById(id);
        if (post != null) {
            try {
                stringRedisTemplate.opsForValue().set(cacheKey,
                        objectMapper.writeValueAsString(post),
                        POST_CACHE_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warn("帖子缓存写入失败: key={}", cacheKey, e);
            }
        }
        return post;
    }

    public List<Post> getByIds(List<Long> ids) {
        return postMapper.selectBatchIds(ids);
    }

    /** 查询用户发布的帖子列表（分页），按创建时间倒序 */
    public List<Post> getByUserId(Long userId, int offset, int limit) {
        return postMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .eq(Post::getStatus, "PUBLISHED")
                        .orderByDesc(Post::getCreatedAt)
                        .last("OFFSET " + offset + " LIMIT " + limit)
        );
    }

    // ===== 写 =====

    public void insert(Post post) {
        postMapper.insert(post);
    }

    public void updateById(Post post) {
        postMapper.updateById(post);
        evictCache(post.getId());
    }

    /** 软删除：状态标记为 DELETED */
    public void softDelete(Long postId) {
        Post update = new Post();
        update.setId(postId);
        update.setStatus("DELETED");
        postMapper.updateById(update);
        evictCache(postId);
    }

    // ===== 图片 =====

    public List<PostImage> getImagesByPostId(Long postId) {
        return postImageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostImage>()
                        .eq(PostImage::getPostId, postId)
                        .orderByAsc(PostImage::getSortOrder));
    }

    public void insertImages(List<PostImage> images) {
        for (PostImage img : images) {
            postImageMapper.insert(img);
        }
    }

    // ===== 缓存 =====

    public void evictCache(Long postId) {
        stringRedisTemplate.delete(CacheKeys.post(postId));
        stringRedisTemplate.delete(CacheKeys.postImages(postId));
    }
}
