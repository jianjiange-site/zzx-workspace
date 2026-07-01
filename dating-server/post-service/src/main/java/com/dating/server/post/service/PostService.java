package com.dating.server.post.service;

import com.dating.server.post.dto.CreatePostRequest;
import com.dating.server.post.dto.PostVO;

import java.util.List;

public interface PostService {

    /** 发帖 */
    PostVO createPost(Long userId, CreatePostRequest request);

    /** 查单个帖子 */
    PostVO getPost(Long postId, Long currentUserId);

    /** 删除（软删除） */
    void deletePost(Long postId, Long userId);

    /** 查用户的帖子列表 */
    List<PostVO> getUserPosts(Long userId, Long currentUserId, int offset, int limit);
}
