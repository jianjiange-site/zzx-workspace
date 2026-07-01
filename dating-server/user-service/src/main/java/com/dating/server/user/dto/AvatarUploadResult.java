package com.dating.server.user.dto;

import lombok.Data;

/**
 * 头像上传结果 DTO
 * 设计文档 §5.4：presign 流程返回 presignedUrl 给 App 直传，objectKey 用于 confirm
 */
@Data
public class AvatarUploadResult {

    /** 客户端用来 PUT 文件直传到对象存储的临时 URL，有效期 5 分钟 */
    private String presignedUrl;

    /** 对象存储的 object_key，confirm 时携带 */
    private String objectKey;
}
