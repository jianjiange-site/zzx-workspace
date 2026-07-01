package com.dating.server.user.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * 对象存储客户端配置
 *
 * 把 dating.object-storage.* 配置转成两个 AWS SDK Bean：
 *   - S3Client：用于检查文件是否存在（headObject）
 *   - S3Presigner：用于生成 presigned URL（临时上传链接）
 *
 * 两个 Bean 都配了：
 *   - endpointOverride → 指向 MinIO 地址（而不是 AWS S3）
 *   - pathStyleAccessEnabled → MinIO 必须开
 */
@Configuration
@RequiredArgsConstructor
public class ObjectStorageConfig {

    private final ObjectStorageProperties props;

    /**
     * S3 客户端
     * 主要用来查文件是否存在（headObject）、后续可能做文件删除等操作。
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.isPathStyleAccess())
                        .build())
                .build();
    }

    /**
     * S3 Presigner（签名器）
     * 专门用来生成 presigned URL，让 App 可以直传文件到 MinIO。
     * 和 S3Client 配置一样，但独立 Bean，互不影响。
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.isPathStyleAccess())
                        .build())
                .build();
    }
}
