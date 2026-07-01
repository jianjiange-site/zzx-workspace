package com.dating.server.post.controller;

import com.dating.server.post.dto.CreatePostRequest;
import com.dating.server.post.dto.PostVO;
import com.dating.server.post.service.PostService;
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

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /** 发帖 */
    @PostMapping
    public PostVO createPost(@RequestParam Long userId,
                             @RequestBody @Valid CreatePostRequest request) {
        return postService.createPost(userId, request);
    }

    /** 查单个帖子 */
    @GetMapping("/{postId}")
    public PostVO getPost(@PathVariable Long postId,
                          @RequestParam(required = false) Long currentUserId) {
        return postService.getPost(postId, currentUserId);
    }

    /** 删除帖子 */
    @DeleteMapping("/{postId}")
    public void deletePost(@PathVariable Long postId,
                           @RequestParam Long userId) {
        postService.deletePost(postId, userId);
    }

    /** 查询用户帖子列表 */
    @GetMapping("/user/{userId}")
    public List<PostVO> getUserPosts(@PathVariable Long userId,
                                     @RequestParam(required = false) Long currentUserId,
                                     @RequestParam(defaultValue = "0") int offset,
                                     @RequestParam(defaultValue = "20") int limit) {
        return postService.getUserPosts(userId, currentUserId, offset, limit);
    }
}
