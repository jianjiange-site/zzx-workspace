package com.dating.server.user.service;

import java.time.Duration;

/**
 * 对象存储操作接口
 *
 * 抽象 S3 / MinIO 的对象存储操作，业务层只调这个接口，
 * 不直接依赖 AWS SDK。后续切到阿里云 OSS / 腾讯云 COS
 * 只要加一个新实现就行。
 *
 * 当前实现：S3ObjectStorage（AWS SDK → MinIO）
 */
public interface ObjectStorage {

    /**
     * 生成 presigned PUT URL
     *
     * 客户端拿到这个 URL 后可以直接 PUT 文件到对象存储，
     * 不用经过业务服务器。URL 过期后失效。
     *
     * @param objectKey 对象存储中的文件路径，例如 "avatar/123/uuid.jpg"
     * @param duration  URL 有效期（eg. 5 分钟）
     * @return 可以直接 PUT 上传的 URL
     */
    String presignedPutUrl(String objectKey, Duration duration);

    /**
     * 检查对象存储中是否存在指定文件
     *
     * 用于头像 confirm 时确认 App 真的上传了文件，
     * 防止客户端拿 presigned URL 后不传就来 confirm。
     *
     * @param objectKey 要检查的文件路径
     * @return true=文件存在，false=不存在
     */
    boolean doesObjectExist(String objectKey);
}
