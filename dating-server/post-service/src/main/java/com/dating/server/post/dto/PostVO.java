package com.dating.server.post.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class PostVO {

    private Long id;
    private Long userId;
    private String content;
    private String type;
    private String visibility;
    private String topic;
    private Boolean allowComment;
    private Integer likeCount;
    private Integer commentCount;
    private Integer shareCount;
    private Integer viewCount;
    private Double score;
    private Boolean liked;
    private List<ImageVO> images;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    public static class ImageVO {
        private Long id;
        private String objectKey;
        private Integer width;
        private Integer height;
        private Integer sortOrder;
    }
}
