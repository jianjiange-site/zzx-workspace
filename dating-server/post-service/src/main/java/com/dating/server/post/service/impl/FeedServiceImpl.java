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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Feed 推荐流服务实现
 *
 * 三路混合推荐，每页 10 个位置按规则分配：
 *   位置 1,2,4,5,7,8,9,10 ← 热门池（FeedScoreJob 每 5 分钟按 Hacker News 分重建）
 *   位置 3               ← 好友时间线（发帖写扩散至 timeline ZSet）
 *   位置 6               ← 冷启动池（发帖同步写入，保证新帖立刻有曝光）
 *
 * 每路取 N 条后按位置混排，已读帖子通过 Redis Set 去重。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final StringRedisTemplate stringRedisTemplate;
    private final PostManager postManager;
    private final PostLikeService postLikeService;

    /** 每页从热门池取的条数（位置 1,2,4,5,7,8,9,10） */
    private static final int HOT_PER_PAGE = 8;
    /** 每页从冷启动池取的条数（位置 6） */
    private static final int COLD_PER_PAGE = 1;
    /** 每次从好友时间线取最新 5 条，供位置 3 筛选 */
    private static final int TIMELINE_FETCH = 5;
    /** 已读去重 Set TTL */
    private static final long SEEN_TTL_DAYS = 7;
    /** 冷启动池 TTL */
    private static final long COLD_POOL_TTL_DAYS = 7;
    /** 冷启动池上限，防内存膨胀 */
    private static final long COLD_POOL_MAX = 10000;

    @Override
    public List<PostVO> getRecommendFeed(Long userId, int pageSize, int offset, int viewerUserType) {
        // offset 作页号（0-based），计算各池在该页的起始偏移
        int page = Math.max(offset, 0);
        long hotOffset = (long) page * HOT_PER_PAGE;
        long coldOffset = (long) page * COLD_PER_PAGE;

        // viewerUserType=0 走主池，>0 走性别分桶
        String hotKey = CacheKeys.hotPoolByGender(viewerUserType);
        String coldKey = CacheKeys.coldPoolByGender(viewerUserType);

        // === 第1步：三路取数据 ===
        List<Long> hotIds = fetchIdsFromPool(hotKey, hotOffset, HOT_PER_PAGE);
        List<Long> timelineIds = fetchIdsFromPool(CacheKeys.timeline(userId), 0, TIMELINE_FETCH);
        List<Long> coldIds = fetchIdsFromPool(coldKey, coldOffset, COLD_PER_PAGE);

        // === 第2步：加载已读 Set ===
        String seenKey = CacheKeys.seenSet(userId);
        Set<String> seen = stringRedisTemplate.opsForSet().members(seenKey);
        if (seen == null) seen = Collections.emptySet();

        // === 第3步：按位置混排（每路不够就用热门池兜底） ===
        List<Long> merged = mergeByPosition(hotIds, timelineIds, coldIds, seen);

        // === 第4步：热门池补齐不足（混排结果不足 PAGE_SIZE 时） ===
        if (merged.size() < pageSize) {
            Set<Long> used = new HashSet<>(merged);
            List<Long> extra = fetchIdsFromPool(hotKey,
                    hotOffset + HOT_PER_PAGE, pageSize - merged.size());
            for (Long id : extra) {
                if (merged.size() >= pageSize) break;
                if (!used.contains(id) && !seen.contains(String.valueOf(id))) {
                    merged.add(id);
                    used.add(id);
                }
            }
        }

        // === 第5步：拼装 PostVO ===
        Set<String> newSeen = new HashSet<>();
        List<PostVO> result = new ArrayList<>();
        for (Long postId : merged) {
            if (result.size() >= pageSize) break;
            Post post = postManager.getCachedById(postId);
            if (post == null || "DELETED".equals(post.getStatus())) continue;

            List<PostImage> images = postManager.getImagesByPostId(postId);
            boolean liked = userId != null && userId > 0
                    && postLikeService.isLiked(postId, userId);
            result.add(toPostVO(post, images, liked));
            newSeen.add(String.valueOf(postId));
        }

        // === 第6步：回写已读 Set ===
        if (!newSeen.isEmpty()) {
            stringRedisTemplate.opsForSet().add(seenKey, newSeen.toArray(new String[0]));
            stringRedisTemplate.expire(seenKey, SEEN_TTL_DAYS, TimeUnit.DAYS);
        }

        return result;
    }

    /**
     * 按设计文档分配 10 个位置：
     *   pos 1,2,4,5,7,8,9,10 ← hot
     *   pos 3               ← timeline（降级 hot）
     *   pos 6               ← cold（降级 hot）
     */
    private List<Long> mergeByPosition(List<Long> hot, List<Long> timeline,
                                       List<Long> cold, Set<String> seen) {
        List<Long> result = new ArrayList<>(10);
        Iterator<Long> hotIter = hot.iterator();
        Iterator<Long> tlIter = timeline.iterator();
        Iterator<Long> coldIter = cold.iterator();

        for (int pos = 1; pos <= 10; pos++) {
            Long id;
            if (pos == 3) {
                id = nextUnseen(tlIter, seen);
                if (id == null) id = nextUnseen(hotIter, seen);
            } else if (pos == 6) {
                id = nextUnseen(coldIter, seen);
                if (id == null) id = nextUnseen(hotIter, seen);
            } else {
                id = nextUnseen(hotIter, seen);
            }
            if (id != null) result.add(id);
        }
        return result;
    }

    /** 从迭代器中取第一个未读的帖子 ID */
    private Long nextUnseen(Iterator<Long> iter, Set<String> seen) {
        while (iter.hasNext()) {
            Long id = iter.next();
            if (!seen.contains(String.valueOf(id))) return id;
        }
        return null;
    }

    /** 从 Redis ZSet 读一段帖子 ID 列表 */
    private List<Long> fetchIdsFromPool(String key, long start, long count) {
        if (count <= 0) return Collections.emptyList();
        Set<String> ids = stringRedisTemplate.opsForZSet()
                .reverseRange(key, start, start + count - 1);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return ids.stream().map(Long::parseLong).collect(Collectors.toList());
    }

    /**
     * 冷启动入池：新帖子发布时同步写入冷启动池
     *
     * 保证新帖在前 5 分钟内（全网池还没重建）就有曝光位。
     * 等下一次 FeedScoreJob 重建时，如果帖子热度够就同时进热门池；
     * 不够也能在冷启动池窗口期内挣到一些曝光。
     */
    @Override
    public void addToColdPool(Long postId, long createdAtEpochMs, int userType) {
        // 写入主池
        addToPool("post:feed:cold", postId, createdAtEpochMs);
        // 写入性别分桶
        if (userType == 1 || userType == 2) {
            addToPool(CacheKeys.coldPoolByGender(userType), postId, createdAtEpochMs);
        }
    }

    /** 写入单个 cold pool 并裁剪 */
    private void addToPool(String key, Long postId, long score) {
        stringRedisTemplate.opsForZSet().add(key, String.valueOf(postId), (double) score);
        stringRedisTemplate.expire(key, COLD_POOL_TTL_DAYS, TimeUnit.DAYS);
        Long size = stringRedisTemplate.opsForZSet().zCard(key);
        if (size != null && size > COLD_POOL_MAX) {
            stringRedisTemplate.opsForZSet().removeRange(key, 0, size - COLD_POOL_MAX - 1);
        }
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
            vo.setImages(images.stream().map(img -> {
                PostVO.ImageVO ivo = new PostVO.ImageVO();
                ivo.setId(img.getId());
                ivo.setObjectKey(img.getObjectKey());
                ivo.setWidth(img.getWidth());
                ivo.setHeight(img.getHeight());
                ivo.setSortOrder(img.getSortOrder());
                return ivo;
            }).toList());
        }
        return vo;
    }
}
