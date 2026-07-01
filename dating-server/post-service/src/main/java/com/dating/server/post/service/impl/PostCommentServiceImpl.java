package com.dating.server.post.service.impl;

import com.dating.server.post.dto.CreateCommentRequest;
import com.dating.server.post.dto.PostCommentVO;
import com.dating.server.post.entity.Post;
import com.dating.server.post.entity.PostComment;
import com.dating.server.post.exception.BizException;
import com.dating.server.post.exception.ErrorCodes;
import com.dating.server.post.manager.PostCommentManager;
import com.dating.server.post.manager.PostManager;
import com.dating.server.post.manager.PostStatsManager;
import com.dating.server.post.service.PostCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostCommentServiceImpl implements PostCommentService {

    private final PostCommentManager postCommentManager;
    private final PostManager postManager;
    private final PostStatsManager postStatsManager;

    @Override
    @Transactional
    public PostCommentVO createComment(Long postId, Long userId, CreateCommentRequest request) {
        Post post = postManager.getById(postId);
        if (post == null) {
            throw new BizException(ErrorCodes.POST_NOT_FOUND, "帖子不存在: " + postId);
        }
        if (!Boolean.TRUE.equals(post.getAllowComment())) {
            throw new BizException(ErrorCodes.POST_NOT_FOUND, "帖子已关闭评论");
        }

        // 如果 parentId 不为空，检查父评论是否存在
        if (request.getParentId() != null) {
            PostComment parent = postCommentManager.getById(request.getParentId());
            if (parent == null) {
                throw new BizException(ErrorCodes.COMMENT_NOT_FOUND, "父评论不存在");
            }
        }

        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(request.getParentId());
        comment.setContent(request.getContent());
        comment.setStatus("PUBLISHED");
        comment.setLikeCount(0);
        postCommentManager.insert(comment);

        post.setCommentCount(post.getCommentCount() + 1);
        postManager.updateById(post);
        postStatsManager.incrementCommentCount(postId, 1);

        log.info("评论成功: commentId={}, postId={}, userId={}", comment.getId(), postId, userId);
        return toCommentVO(comment);
    }

    @Override
    public List<PostCommentVO> getComments(Long postId) {
        List<PostComment> comments = postCommentManager.getByPostId(postId);
        return comments.stream().map(this::toCommentVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        PostComment comment = postCommentManager.getById(commentId);
        if (comment == null) {
            throw new BizException(ErrorCodes.COMMENT_NOT_FOUND, "评论不存在: " + commentId);
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BizException(ErrorCodes.COMMENT_NOT_FOUND, "无权删除他人评论");
        }
        postCommentManager.softDelete(commentId);

        Post post = postManager.getById(comment.getPostId());
        if (post != null) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
            postManager.updateById(post);
            postStatsManager.incrementCommentCount(comment.getPostId(), -1);
        }

        log.info("评论已删除: commentId={}, userId={}", commentId, userId);
    }

    private PostCommentVO toCommentVO(PostComment comment) {
        PostCommentVO vo = new PostCommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setUserId(comment.getUserId());
        vo.setParentId(comment.getParentId());
        vo.setContent(comment.getContent());
        vo.setLikeCount(comment.getLikeCount());
        vo.setCreatedAt(comment.getCreatedAt());
        return vo;
    }
}
