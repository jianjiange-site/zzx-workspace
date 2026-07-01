package com.dating.server.post.service;

public interface PostLikeService {

    /** 点赞 */
    void like(Long postId, Long userId);

    /** 取消点赞 */
    void unlike(Long postId, Long userId);

    /** 查询是否已点赞 */
    boolean isLiked(Long postId, Long userId);
}
