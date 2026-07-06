package com.dating.server.gateway.controller;

import com.dating.server.gateway.auth.JwtUtil;
import com.dating.server.gateway.client.UserServiceClient;
import com.dating.server.gateway.common.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录注册入口。3 种方式统一走 user-service gRPC 解析身份，签发 JWT。
 * 这些路径被 JwtAuthFilter.shouldNotFilter 排除，不需要 token 即可访问。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserServiceClient userServiceClient;
    private final JwtUtil jwtUtil;

    /** 手机验证码登录：验证码 "666666" 为 dev 环境万能码 */
    @PostMapping("/login/phone")
    public R<Map<String, Object>> loginByPhone(@RequestBody @Valid PhoneLoginRequest body) {
        var resp = userServiceClient.loginByPhone(body.getPhone(), body.getCode());
        String token = jwtUtil.generateToken(resp.getUserId());
        return R.ok(Map.of("userId", resp.getUserId(), "token", token, "pending", resp.getPending()));
    }

    /** 三方授权登录：platform=google/apple/wechat，由 user-service 做身份解析 */
    @PostMapping("/login/third-party")
    public R<Map<String, Object>> loginByThirdParty(@RequestBody @Valid ThirdPartyLoginRequest body) {
        var resp = userServiceClient.loginByThirdParty(body.getPlatform(), body.getThirdPartyUserId(), body.getDeviceId());
        String token = jwtUtil.generateToken(resp.getUserId());
        return R.ok(Map.of("userId", resp.getUserId(), "token", token, "pending", resp.getPending()));
    }

    /** 设备快速登录：首次打开 App 免注册体验，后续可绑定手机号升级为完整账户 */
    @PostMapping("/login/device")
    public R<Map<String, Object>> loginByDevice(@RequestBody @Valid DeviceLoginRequest body) {
        var resp = userServiceClient.loginByDevice(body.getDeviceId(), body.getPlatform());
        String token = jwtUtil.generateToken(resp.getUserId());
        return R.ok(Map.of("userId", resp.getUserId(), "token", token, "pending", resp.getPending()));
    }

    @Data
    public static class PhoneLoginRequest {

        private String phone;
        @NotBlank
        private String code;
    }

    @Data
    public static class ThirdPartyLoginRequest {
        @NotBlank
        private String platform;
        @NotBlank
        private String thirdPartyUserId;
        private String deviceId;
    }

    @Data
    public static class DeviceLoginRequest {
        @NotBlank
        private String deviceId;
        @NotBlank
        private String platform;
    }
}
