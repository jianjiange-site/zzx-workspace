package com.dating.server.post.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class PostCommentVO {

    private Long id;
    private Long postId;
    private Long userId;
    private Long parentId;
    private String content;
    private Integer likeCount;
    private Instant createdAt;
}
