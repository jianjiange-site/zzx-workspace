package com.dating.server.user.service;

import com.dating.server.user.dto.AvatarUploadResult;

/**
 * 头像服务接口
 * 设计文档 §5.4：presigned PUT 直传模式，服务端不转发文件流
 */
public interface UserAvatarService {

    /**
     * 签头像上传 URL
     *
     * @param userId 用户 ID（决定 objectKey 路径）
     * @param ext    文件扩展名（jpg/png/webp，不含点）
     * @return presignedUrl + objectKey
     */
    AvatarUploadResult presignAvatarUpload(Long userId, String ext);

    /**
     * 确认头像上传完成
     * 校验对象存储中存在 + 更新 custom_avatar JSONB + 清缓存
     *
     * @param userId    用户 ID
     * @param objectKey presign 返回的 objectKey
     */
    void confirmAvatarUpload(Long userId, String objectKey);
}
