package com.dating.server.user.dto;

import lombok.Data;

/**
 * 兴趣标签 VO
 * 设计文档 §5.6：picKey 是 object_key，App 侧自拼 CDN URL
 */
@Data
public class UserInterestVO {

    private String type;        // IMAGE / TEXT
    private String picKey;      // IMAGE 类型时，对象存储的 object_key
    private String content;     // TEXT 类型时，文字内容
    private Integer sortOrder;
}
