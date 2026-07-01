package com.dating.server.user.dto;

import lombok.Data;

/**
 * 替换兴趣标签的单项
 * 设计文档 §5.6：图片标签 ≤ 9，文字标签 ≤ 50
 */
@Data
public class ReplaceInterestItem {

    private String type;        // IMAGE / TEXT
    private String picKey;      // IMAGE 时必填
    private String content;     // TEXT 时必填
    private Integer sortOrder;
}
