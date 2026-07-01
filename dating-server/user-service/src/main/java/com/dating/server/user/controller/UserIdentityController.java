package com.dating.server.user.controller;

import com.dating.server.user.constant.AppName;
import com.dating.server.user.dto.IdentityResolveResult;
import com.dating.server.user.service.UserIdentityService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 身份解析入口：手机号/第三方/设备三种方式的登录注册。
 * 所有接口接收 gateway 已校验的凭证，查找或创建用户后返回 userId。
 */
@RestController
@RequestMapping("/api/v1/identity")
@RequiredArgsConstructor
public class UserIdentityController {

    private final UserIdentityService userIdentityService;

    /**
     * 手机号登录/注册：校验短信验证码通过后，用 E.164 格式手机号查找或创建用户。
     * 新用户会创建 placeholder（pending=true），走 onboarding 补齐资料。
     *
     * @param request phone（E.164 格式字符串，必填）
     * @return userId + pending 标记；用户已存在不会重复创建
     */
    @PostMapping("/phone")
    public IdentityResolveResult resolveByPhone(@RequestBody @jakarta.validation.Valid PhoneRequest request) {
        return userIdentityService.resolveOrCreateByPhone(request.getPhone(), AppName.ZZX_DATING.getValue());
    }

    /**
     * 第三方登录/注册：用平台（Google/Apple/Facebook）+ 第三方 userId 查找或创建用户。
     * 第三方 token 已在 gateway 层校验通过，本接口只做身份解析。
     *
     * @param request platform（三方平台名）、thirdPartyUserId（三方平台用户 ID）、googleEmail（Google 邮箱，可选）
     * @return userId + pending 标记
     */
    @PostMapping("/third-party")
    public IdentityResolveResult resolveByThirdParty(@RequestBody @jakarta.validation.Valid ThirdPartyRequest request) {
        return userIdentityService.resolveOrCreateByThirdParty(
                request.getPlatform(), request.getThirdPartyUserId(),
                AppName.ZZX_DATING.getValue(), request.getGoogleEmail());
    }

    /**
     * 设备快速登录：用设备 ID 查找或创建用户。无需短信/三方授权，适用于首次打开 App 快速体验。
     * 后续用户绑手机号后升级为完整账户。
     *
     * @param request deviceId（设备唯一标识，如 IDFV/SSAID）、platform（iOS/Android）
     * @return userId + pending 标记；新用户为 placeholder
     */
    @PostMapping("/device")
    public IdentityResolveResult resolveByDevice(@RequestBody @jakarta.validation.Valid DeviceRequest request) {
        return userIdentityService.resolveOrCreateByDevice(
                request.getDeviceId(), request.getPlatform(), AppName.ZZX_DATING.getValue());
    }

    @Data
    public static class PhoneRequest {
        @NotBlank
        private String phone;
    }

    @Data
    public static class ThirdPartyRequest {
        @NotBlank
        private String platform;
        @NotBlank
        private String thirdPartyUserId;
        private String googleEmail;
    }

    @Data
    public static class DeviceRequest {
        @NotBlank
        private String deviceId;
        @NotBlank
        private String platform;
    }
}
