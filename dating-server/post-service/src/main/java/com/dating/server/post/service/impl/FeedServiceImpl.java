package com.dating.server.post.service.impl;

import com.dating.server.post.constant.CacheKeys;
import com.dating.server.post.dto.PostVO;
import com.dating.server.post.entity.Post;
import com.dating.server.post.entity.PostImage;
import com.dating.server.post.manager.PostManager;
import com.dating.server.post.service.FeedService;
import com.dating.server.post.service.PostLikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Feed 推荐服务实现
 *
 * 当前实现：从热门池（ZSet）读取，按 score 倒序
 * TODO: 三路并行取（热门 + 好友 + 冷启动）+ 布隆去重 + 混排
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final StringRedisTemplate stringRedisTemplate;
    private final PostManager postManager;
    private final PostLikeService postLikeService;

    /** 热门池 Redis key */
    static final String HOT_POOL_KEY = "post:feed:hot";
    /** 冷启动池 Redis key */
    private static final String COLD_POOL_KEY = "post:feed:cold";
    /** 冷启动池 TTL：7 天 */
    private static final long COLD_POOL_TTL_DAYS = 7;

    @Override
    public List<PostVO> getRecommendFeed(Long userId, int pageSize, int offset) {
        // 从热门池取帖子 ID
        Set<String> postIdStrs = stringRedisTemplate.opsForZSet()
                .reverseRange(HOT_POOL_KEY, offset, offset + pageSize - 1);

        if (postIdStrs == null || postIdStrs.isEmpty()) {
            return Collections.emptyList();
        }

        List<PostVO> result = new ArrayList<>(postIdStrs.size());
        for (String idStr : postIdStrs) {
            Long postId = Long.parseLong(idStr);
            Post post = postManager.getCachedById(postId);
            if (post == null || "DELETED".equals(post.getStatus())) {
                continue;
            }

            List<PostImage> images = postManager.getImagesByPostId(postId);
            boolean liked = userId != null && userId > 0 &&
                    postLikeService.isLiked(postId, userId);

            result.add(toPostVO(post, images, liked));
        }

        return result;
    }

    /**
     * 冷启动入池：新帖子发布时调用
     * 把新帖子加入冷启动池，保证新内容能被看到
     */
    public void addToColdPool(Long postId, long createdAtEpochMs) {
        stringRedisTemplate.opsForZSet().add(COLD_POOL_KEY, String.valueOf(postId), (double) createdAtEpochMs);
        stringRedisTemplate.expire(COLD_POOL_KEY, COLD_POOL_TTL_DAYS, TimeUnit.DAYS);
    }

    private PostVO toPostVO(Post post, List<PostImage> images, boolean liked) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setUserId(post.getUserId());
        vo.setContent(post.getContent());
        vo.setType(post.getType());
        vo.setVisibility(post.getVisibility());
        vo.setTopic(post.getTopic());
        vo.setAllowComment(post.getAllowComment());
        vo.setLikeCount(post.getLikeCount());
        vo.setCommentCount(post.getCommentCount());
        vo.setShareCount(post.getShareCount());
        vo.setViewCount(post.getViewCount());
        vo.setScore(post.getScore());
        vo.setLiked(liked);
        vo.setCreatedAt(post.getCreatedAt());
        vo.setUpdatedAt(post.getUpdatedAt());

        if (images != null) {
            List<PostVO.ImageVO> imageVOs = images.stream().map(img -> {
                PostVO.ImageVO ivo = new PostVO.ImageVO();
                ivo.setId(img.getId());
                ivo.setObjectKey(img.getObjectKey());
                ivo.setWidth(img.getWidth());
                ivo.setHeight(img.getHeight());
                ivo.setSortOrder(img.getSortOrder());
                return ivo;
            }).toList();
            vo.setImages(imageVOs);
        }

        return vo;
    }
}
