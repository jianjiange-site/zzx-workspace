package com.dating.server.post.service;

import com.dating.server.post.dto.CreateCommentRequest;
import com.dating.server.post.dto.PostCommentVO;

import java.util.List;

public interface PostCommentService {

    /** 发表评论 */
    PostCommentVO createComment(Long postId, Long userId, CreateCommentRequest request);

    /** 查询帖子评论列表 */
    List<PostCommentVO> getComments(Long postId);

    /** 删除评论 */
    void deleteComment(Long commentId, Long userId);
}
