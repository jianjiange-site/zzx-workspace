package com.dating.server.post.service.impl;

import com.dating.server.post.entity.Post;
import com.dating.server.post.entity.PostLike;
import com.dating.server.post.exception.BizException;
import com.dating.server.post.exception.ErrorCodes;
import com.dating.server.post.manager.PostLikeManager;
import com.dating.server.post.manager.PostManager;
import com.dating.server.post.manager.PostStatsManager;
import com.dating.server.post.service.PostLikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostLikeServiceImpl implements PostLikeService {

    private final PostLikeManager postLikeManager;
    private final PostManager postManager;
    private final PostStatsManager postStatsManager;

    @Override
    @Transactional
    public void like(Long postId, Long userId) {
        Post post = postManager.getById(postId);
        if (post == null) {
            throw new BizException(ErrorCodes.POST_NOT_FOUND, "帖子不存在: " + postId);
        }
        if (postLikeManager.isLiked(postId, userId)) {
            throw new BizException(ErrorCodes.LIKE_ALREADY, "已点赞");
        }
        PostLike like = new PostLike();
        like.setPostId(postId);
        like.setUserId(userId);
        postLikeManager.insert(like);

        post.setLikeCount(post.getLikeCount() + 1);
        postManager.updateById(post);
        postStatsManager.incrementLikeCount(postId, 1);

        log.info("点赞成功: postId={}, userId={}", postId, userId);
    }

    @Override
    @Transactional
    public void unlike(Long postId, Long userId) {
        PostLike like = postLikeManager.getByPostIdAndUserId(postId, userId);
        if (like == null) {
            throw new BizException(ErrorCodes.LIKE_NOT_FOUND, "未点赞");
        }
        postLikeManager.delete(like);

        Post post = postManager.getById(postId);
        if (post != null) {
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            postManager.updateById(post);
            postStatsManager.incrementLikeCount(postId, -1);
        }

        log.info("取消点赞: postId={}, userId={}", postId, userId);
    }

    @Override
    public boolean isLiked(Long postId, Long userId) {
        return postLikeManager.isLiked(postId, userId);
    }
}
