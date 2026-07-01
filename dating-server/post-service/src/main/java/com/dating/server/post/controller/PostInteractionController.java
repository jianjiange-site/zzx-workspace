package com.dating.server.post.controller;

import com.dating.server.post.dto.CreateCommentRequest;
import com.dating.server.post.dto.PostCommentVO;
import com.dating.server.post.service.PostCommentService;
import com.dating.server.post.service.PostLikeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostInteractionController {

    private final PostLikeService postLikeService;
    private final PostCommentService postCommentService;

    // ===== 点赞 =====

    /** 点赞 */
    @PostMapping("/{postId}/like")
    public void like(@PathVariable Long postId, @RequestParam Long userId) {
        postLikeService.like(postId, userId);
    }

    /** 取消点赞 */
    @DeleteMapping("/{postId}/like")
    public void unlike(@PathVariable Long postId, @RequestParam Long userId) {
        postLikeService.unlike(postId, userId);
    }

    /** 查询点赞状态 */
    @GetMapping("/{postId}/like/status")
    public Map<String, Boolean> isLiked(@PathVariable Long postId, @RequestParam Long userId) {
        return Map.of("liked", postLikeService.isLiked(postId, userId));
    }

    // ===== 评论 =====

    /** 发表评论 */
    @PostMapping("/{postId}/comments")
    public PostCommentVO createComment(@PathVariable Long postId,
                                       @RequestParam Long userId,
                                       @RequestBody @Valid CreateCommentRequest request) {
        return postCommentService.createComment(postId, userId, request);
    }

    /** 查询评论列表 */
    @GetMapping("/{postId}/comments")
    public List<PostCommentVO> getComments(@PathVariable Long postId) {
        return postCommentService.getComments(postId);
    }

    /** 删除评论 */
    @DeleteMapping("/comments/{commentId}")
    public void deleteComment(@PathVariable Long commentId, @RequestParam Long userId) {
        postCommentService.deleteComment(commentId, userId);
    }
}
