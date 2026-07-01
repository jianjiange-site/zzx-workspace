package com.dating.server.user.controller;

import com.dating.server.user.dto.AvatarUploadResult;
import com.dating.server.user.service.UserAvatarService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 头像上传：App 直传对象存储的 presign/confirm 流程。
 * 服务端不读文件流，只签 URL 和落库 objectKey。
 */
@RestController
@RequestMapping("/api/v1/avatars")
@RequiredArgsConstructor
public class UserAvatarController {

    private final UserAvatarService userAvatarService;

    /**
     * 申请头像上传 presigned URL：生成 objectKey 并返回 S3 presigned PUT URL。
     * App 拿到 URL 后直传文件到对象存储，不经过业务服务器。URL 有效期 5 分钟。
     *
     * @param request userId（用户 ID）、ext（文件扩展名，限 jpg/jpeg/png/webp）
     * @return presignedUrl（直传地址）+ objectKey（上传成功后需传给 confirm 接口）
     */
    @PostMapping("/presign")
    public AvatarUploadResult presign(@RequestBody @jakarta.validation.Valid PresignRequest request) {
        return userAvatarService.presignAvatarUpload(request.getUserId(), request.getExt());
    }

    /**
     * 确认头像上传完成：App 直传成功后调用，校验对象存储中文件存在，更新用户头像 JSONB，清除缓存。
     * 上传失败（文件不存在 / 损坏）抛 BizException。
     *
     * @param request userId（用户 ID）、objectKey（presign 返回的 objectKey）
     */
    @PostMapping("/confirm")
    public void confirm(@RequestBody @jakarta.validation.Valid ConfirmRequest request) {
        userAvatarService.confirmAvatarUpload(request.getUserId(), request.getObjectKey());
    }

    @Data
    public static class PresignRequest {
        @NotNull
        private Long userId;
        @NotBlank
        private String ext;
    }

    @Data
    public static class ConfirmRequest {
        @NotNull
        private Long userId;
        @NotBlank
        private String objectKey;
    }
}
