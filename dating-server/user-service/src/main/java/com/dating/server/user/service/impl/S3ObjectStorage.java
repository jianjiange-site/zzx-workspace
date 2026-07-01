package com.dating.server.user.service.impl;

import com.dating.server.user.config.ObjectStorageProperties;
import com.dating.server.user.service.ObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/**
 * S3 / MinIO 对象存储实现
 *
 * 基于 AWS SDK v2，但因为配了 endpointOverride + pathStyleAccess，
 * 实际指向的是 MinIO（或任何兼容 S3 协议的对象存储）。
 *
 * 两个功能：
 *   1. presignedPutUrl → 生成临时上传链接（给 App 直传文件用）
 *   2. doesObjectExist → 检查文件是否存在（防止假确认）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3ObjectStorage implements ObjectStorage {

    private final ObjectStorageProperties props;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    /**
     * 生成 presigned PUT URL
     *
     * 流程：
     *   1. 构造 PutObjectRequest（指定桶名 + 文件路径）
     *   2. 用 S3Presigner 签名，生成临时 URL（指定有效期）
     *   3. 返回 URL 字符串
     *
     * App 拿到这个 URL 后，直接发 PUT 请求把文件二进制传上去，
     * 不需要在请求里带任何认证信息（签名已经编进 URL 了）。
     */
    @Override
    public String presignedPutUrl(String objectKey, Duration duration) {

        // 第1步：构造 PutObjectRequest，指定存到哪个桶、哪个路径
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(objectKey)
                .build();

        // 第2步：把 PutObjectRequest 包一层，加上有效期
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(duration)          // URL 有效期（过了就不能用了）
                .putObjectRequest(putRequest)
                .build();

        // 第3步：签名生成 URL
        String url = s3Presigner.presignPutObject(presignRequest).url().toString();

        log.debug("生成 presigned PUT URL: bucket={}, key={}", props.getBucket(), objectKey);
        return url;
    }

    /**
     * 检查对象存储中是否存在指定文件
     *
     * 调 S3 的 headObject 接口（只查元数据，不下载文件体）。
     * 如果文件不存在，S3 会抛 NoSuchKeyException，我们捕获后返回 false。
     *
     * @param objectKey 文件路径
     * @return true=存在，false=不存在
     */
    @Override
    public boolean doesObjectExist(String objectKey) {
        try {
            // headObject 只拿文件头信息（大小/类型等），不下载内容
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            // 文件不存在 → 返回 false
            return false;
        }
    }
}
