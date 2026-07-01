package com.dating.server.user.service.impl;

import com.dating.server.user.dto.AvatarUploadResult;
import com.dating.server.user.exception.BizException;
import com.dating.server.user.exception.ErrorCodes;
import com.dating.server.user.manager.UserInfoManager;
import com.dating.server.user.service.ObjectStorage;
import com.dating.server.user.service.UserAvatarService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 头像服务实现
 *
 * 头像上传采用 presigned URL 直传模式：
 *
 * 流程（三步走）：
 *   App → 1. POST /presign {ext} → 服务器返回一个临时上传链接
 *   App → 2. 用这个链接直接把文件上传到对象存储（不用经过服务器）
 *   App → 3. POST /confirm {objectKey} → 服务器更新数据库
 *
 * 好处：文件不经过服务器，节省带宽，上传更快。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAvatarServiceImpl implements UserAvatarService {

    /**
     * 允许上传的图片格式白名单
     * 不符合的会在第1步就被拒绝
     */
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");

    /** presigned URL 的有效期（分钟），过期后需要重新申请 */
    private static final int PRESIGN_TTL_MINUTES = 5;

    private final UserInfoManager userInfoManager;
    private final ObjectMapper objectMapper;  // Jackson JSON 工具
    private final ObjectStorage objectStorage;

    /**
     * ===== 第1步：申请头像上传链接 =====
     *
     * App 上传头像前，先调用这个接口获取一个临时上传 URL。
     *
     * 流程：
     *   1. 校验文件后缀是否在白名单里（jpg/png/webp）
     *   2. 生成 object_key：avatar/{userId}/{随机uuid}.{后缀}
     *   3. 调用 S3 对象存储服务，生成一个 presigned PUT URL（5分钟内有效）
     *
     * @param userId 谁在传头像
     * @param ext    文件后缀（不带点，如 "jpg"、"png"）
     * @return presignedUrl（上传链接）+ objectKey（上传成功后的确认凭证）
     */
    @Override
    public AvatarUploadResult presignAvatarUpload(Long userId, String ext) {

        // 第1步：校验文件后缀是否允许
        // 把后缀转小写（防止前端传 "JPG" 或 "Jpg"）
        String lowerExt = ext.toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(lowerExt)) {
            // 不在白名单里 → 拒绝，错误码 10101
            throw new BizException(ErrorCodes.AVATAR_EXTENSION_NOT_ALLOWED,
                    "头像格式不支持: " + ext);
        }

        // 第2步：生成 object_key（对象存储中的文件路径）
        // 格式：avatar/{userId}/{uuid}.{ext}
        // 例子：avatar/123456789/550e8400-e29b-41d4-a716-446655440000.jpg
        // 这样不同用户的头像在不同目录，方便管理
        String objectKey = String.format("avatar/%s/%s.%s", userId, UUID.randomUUID(), lowerExt);

        // 第3步：生成 presigned URL，有效期 5 分钟
        String presignedUrl = objectStorage.presignedPutUrl(objectKey, Duration.ofMinutes(PRESIGN_TTL_MINUTES));

        // 组装结果
        AvatarUploadResult result = new AvatarUploadResult();
        result.setPresignedUrl(presignedUrl);   // 给 App 用来上传文件
        result.setObjectKey(objectKey);          // 给第2步 confirm 用

        log.info("头像 presign: userId={}, objectKey={}", userId, objectKey);
        return result;
    }

    /**
     * ===== 第3步：确认头像上传完成 =====
     *
     * App 用 presigned URL 把文件上传到对象存储后，调用这个接口告诉服务器：
     * "我传好了，这是我的头像文件路径（objectKey）"
     *
     * 服务器做的事：
     *   1. 去对象存储检查文件是不是真的上传成功了
     *   2. 把 objectKey 写入 user_info 表的 custom_avatar JSONB 字段
     *   3. 清除 Redis 缓存，下次查询时重新加载
     *
     * @param userId   用户 ID
     * @param objectKey 第1步 presign 返回的文件路径
     */
    @Override
    @Transactional
    public void confirmAvatarUpload(Long userId, String objectKey) {

        // 第1步：去对象存储检查文件是否存在
        // 防止 App 拿了 presign URL 但没真上传就来 confirm
        if (!objectStorage.doesObjectExist(objectKey)) {
            throw new BizException(ErrorCodes.AVATAR_UPLOAD_NOT_FOUND, "头像文件未上传: " + objectKey);
        }

        // 第2步：构造 custom_avatar 的 JSON 结构体
        // 数据库 JSONB 字段存三个尺寸的 key：
        //   originalKey：原图
        //   minKey：小图缩略图（MVP 阶段跟原图 key 一样）
        //   midKey：中图缩略图（MVP 阶段跟原图 key 一样）
        // 将来缩略图功能上线后，这里生成不同尺寸的图片 key
        Map<String, String> avatarJson = new HashMap<>();
        avatarJson.put("originalKey", objectKey);  // 原图
        avatarJson.put("minKey", objectKey);        // 小图（当前同原图）
        avatarJson.put("midKey", objectKey);         // 中图（当前同原图）

        try {
            // 把 Map 转成 JSON 字符串
            String json = objectMapper.writeValueAsString(avatarJson);
            // 写入数据库 user_info.custom_avatar
            userInfoManager.updateCustomAvatar(userId, json);
        } catch (Exception e) {
            log.error("头像 JSON 写入失败, userId={}", userId, e);
            throw new RuntimeException("头像更新失败", e);
        }

        // 第3步：删除 Redis 缓存（头像变了，下次查询重新加载）
        userInfoManager.evictProfileCache(userId);

        log.info("头像 confirm 完成: userId={}, objectKey={}", userId, objectKey);
    }
}
