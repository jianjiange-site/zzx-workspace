package com.dating.server.user.controller;

import com.dating.server.user.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 身份解析接口集成测试
 *
 * 覆盖三种登录注册方式：手机号 / 第三方 / 设备。
 * 每种都测"首次注册"和"重复调用返回相同 userId"两个场景。
 */
class UserIdentityIntegrationTest extends AbstractIntegrationTest {

    /**
     * 手机号登录注册
     *
     * 场景：
     *   1. 首次用手机会新注册用户 → 拿到 userId + pending=true
     *   2. 再用相同手机号注册 → 返回同一个 userId（幂等）
     */
    @SuppressWarnings("unchecked")
    @Test
    void phoneRegister_shouldCreateAndReturnSameUser() {
        String phone = "+8613800000001";

        // 第1次：新用户注册
        ResponseEntity<Map> first = rest.postForEntity(
                "/api/v1/identity/phone",
                Map.of("phone", phone),
                Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().get("userId")).isNotNull();
        assertThat(first.getBody().get("pending")).isEqualTo(true);

        Long userId = ((Number) first.getBody().get("userId")).longValue();

        // 第2次：重复调用，应返回相同 userId
        ResponseEntity<Map> second = rest.postForEntity(
                "/api/v1/identity/phone",
                Map.of("phone", phone),
                Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull();
        assertThat(((Number) second.getBody().get("userId")).longValue()).isEqualTo(userId);
    }

    /**
     * 设备登录注册
     *
     * 场景：首次设备注册 → 拿到 userId，重复调用返回相同 userId
     */
    @SuppressWarnings("unchecked")
    @Test
    void deviceRegister_shouldCreateAndReturnSameUser() {
        String deviceId = "test-device-uuid-12345";

        // 第1次：新设备注册
        ResponseEntity<Map> first = rest.postForEntity(
                "/api/v1/identity/device",
                Map.of("deviceId", deviceId, "platform", "ios"),
                Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().get("userId")).isNotNull();

        Long userId = ((Number) first.getBody().get("userId")).longValue();

        // 第2次：重复调用
        ResponseEntity<Map> second = rest.postForEntity(
                "/api/v1/identity/device",
                Map.of("deviceId", deviceId, "platform", "ios"),
                Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) second.getBody().get("userId")).longValue()).isEqualTo(userId);
    }

    /**
     * 第三方登录注册
     *
     * 场景：首次第三方注册 → 拿到 userId，重复调用返回相同 userId
     */
    @SuppressWarnings("unchecked")
    @Test
    void thirdPartyRegister_shouldCreateAndReturnSameUser() {
        String platform = "google";
        String tpUserId = "google-uid-99999";

        // 第1次
        ResponseEntity<Map> first = rest.postForEntity(
                "/api/v1/identity/third-party",
                Map.of("platform", platform, "thirdPartyUserId", tpUserId),
                Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().get("userId")).isNotNull();

        Long userId = ((Number) first.getBody().get("userId")).longValue();

        // 第2次：幂等
        ResponseEntity<Map> second = rest.postForEntity(
                "/api/v1/identity/third-party",
                Map.of("platform", platform, "thirdPartyUserId", tpUserId),
                Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) second.getBody().get("userId")).longValue()).isEqualTo(userId);
    }

    /**
     * 手机号参数校验：空 phone 返回 400
     */
    @SuppressWarnings("unchecked")
    @Test
    void phoneRegister_emptyPhone_shouldReturn400() {
        ResponseEntity<Map> response = rest.postForEntity(
                "/api/v1/identity/phone",
                Map.of("phone", ""),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
