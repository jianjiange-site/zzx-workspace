package com.dating.server.post.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreatePostRequest {

    @Size(max = 2000, message = "帖子内容不能超过2000字")
    private String content;

    private String type = "TEXT";

    private String visibility = "PUBLIC";

    @Size(max = 100)
    private String topic;

    private Boolean allowComment = true;

    /** 图片列表：最多9张 */
    /** 发布者用户类型：1=BH 2=DH 0=未知 */
    private Integer userType;

    @Size(max = 9, message = "最多上传9张图片")
    private List<ImageItem> images;

    @Data
    public static class ImageItem {
        private String objectKey;
        private Integer width;
        private Integer height;
    }
}
