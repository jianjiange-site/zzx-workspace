package com.dating.server.post.service.impl;

import com.dating.server.post.dto.CreatePostRequest;
import com.dating.server.post.dto.PostVO;
import com.dating.server.post.entity.Post;
import com.dating.server.post.entity.PostImage;
import com.dating.server.post.exception.BizException;
import com.dating.server.post.exception.ErrorCodes;
import com.dating.server.post.manager.PostManager;
import com.dating.server.post.manager.PostStatsManager;
import com.dating.server.post.mq.PostFanoutProducer;
import com.dating.server.post.service.FeedService;
import com.dating.server.post.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostManager postManager;
    private final PostStatsManager postStatsManager;
    private final PostFanoutProducer postFanoutProducer;
    private final FeedService feedService;

    @Override
    @Transactional
    public PostVO createPost(Long userId, CreatePostRequest request) {
        Post post = new Post();
        post.setUserId(userId);
        post.setContent(request.getContent());
        post.setType(request.getType());
        post.setVisibility(request.getVisibility());
        post.setTopic(request.getTopic());
        post.setAllowComment(request.getAllowComment());
        post.setStatus("PUBLISHED");
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setShareCount(0);
        post.setViewCount(0);
        post.setScore(0.0);
        post.setUserType(request.getUserType());
        postManager.insert(post);

        // 写入图片
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            List<PostImage> images = request.getImages().stream().map(img -> {
                PostImage pi = new PostImage();
                pi.setPostId(post.getId());
                pi.setObjectKey(img.getObjectKey());
                pi.setWidth(img.getWidth());
                pi.setHeight(img.getHeight());
                return pi;
            }).collect(Collectors.toList());
            postManager.insertImages(images);
        }

        // 写扩散：异步通知粉丝有新帖子（不阻塞事务，失败只打日志）
        postFanoutProducer.sendPostCreated(post.getId(), userId, post.getCreatedAt().toEpochMilli());

        // 冷启动入池：新帖子马上能出现在推荐流里
        feedService.addToColdPool(post.getId(), post.getCreatedAt().toEpochMilli(),
                Optional.ofNullable(request.getUserType()).orElse(0));

        log.info("帖子创建成功: postId={}, userId={}", post.getId(), userId);
        return toPostVO(post, null, false);
    }

    @Override
    public PostVO getPost(Long postId, Long currentUserId) {
        Post post = postManager.getCachedById(postId);
        if (post == null) {
            throw new BizException(ErrorCodes.POST_NOT_FOUND, "帖子不存在: " + postId);
        }
        if ("DELETED".equals(post.getStatus())) {
            throw new BizException(ErrorCodes.POST_DELETED, "帖子已删除: " + postId);
        }

        List<PostImage> images = postManager.getImagesByPostId(postId);
        return toPostVO(post, images, false);
    }

    @Override
    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = postManager.getById(postId);
        if (post == null) {
            throw new BizException(ErrorCodes.POST_NOT_FOUND, "帖子不存在: " + postId);
        }
        if (!post.getUserId().equals(userId)) {
            throw new BizException(ErrorCodes.POST_NOT_FOUND, "无权删除他人帖子");
        }
        postManager.softDelete(postId);
        log.info("帖子已删除: postId={}, userId={}", postId, userId);
    }

    @Override
    public List<PostVO> getUserPosts(Long userId, Long currentUserId, int offset, int limit) {
        List<Post> posts = postManager.getByUserId(userId, offset, limit);
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }
        return posts.stream().map(post -> {
            List<PostImage> images = postManager.getImagesByPostId(post.getId());
            return toPostVO(post, images, false);
        }).collect(Collectors.toList());
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
            }).collect(Collectors.toList()));
        }
        return vo;
    }
}
