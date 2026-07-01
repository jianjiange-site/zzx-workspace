package com.dating.server.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对象存储配置映射
 *
 * 读取 application.yml 里 dating.object-storage 开头的配置项，
 * 自动绑定到这个类的字段上。
 *
 * 配置来源（application-dev.yml）：
 *   dating:
 *     object-storage:
 *       endpoint: https://minio-api.jianjiange.site
 *       access-key: admin
 *       secret-key: ...
 *       bucket: dating-zzx
 */
@Data
@Component
@ConfigurationProperties(prefix = "dating.object-storage")
public class ObjectStorageProperties {

    /** 对象存储服务地址（MinIO / S3 的 API 地址） */
    private String endpoint;

    /** 区域，MinIO 填 us-east-1 就行 */
    private String region;

    /** 访问密钥 ID（相当于用户名） */
    private String accessKey;

    /** 访问密钥密码 */
    private String secretKey;

    /**
     * 是否使用路径风格访问
     * MinIO 必须为 true（S3 默认 virtual hosted 风格）
     */
    private boolean pathStyleAccess = true;

    /** 存储桶名称（头像文件存哪个桶里） */
    private String bucket;
}
