package com.dating.server.user.controller;

import com.dating.server.user.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户资料接口集成测试
 *
 * 覆盖：查资料、改资料、Onboarding、封禁检查、兴趣标签替换。
 * 需要一个已注册的用户作为前置条件。
 */
class UserProfileIntegrationTest extends AbstractIntegrationTest {

    private Long userId;

    /**
     * 每个测试前先注册一个用户
     * 这样每个测试用例都有可用的 userId，不用重复写注册逻辑。
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        // 用设备注册创建一个测试用户（设备注册不需要额外依赖）
        ResponseEntity<Map> response = rest.postForEntity(
                "/api/v1/identity/device",
                Map.of("deviceId", "test-profile-device", "platform", "ios"),
                Map.class);
        userId = ((Number) response.getBody().get("userId")).longValue();
    }

    /**
     * 查资料：正常用户能查到基本字段
     */
    @SuppressWarnings("unchecked")
    @Test
    void getProfile_shouldReturnUserInfo() {
        ResponseEntity<Map> response = rest.getForEntity(
                "/api/v1/profiles/{userId}", Map.class, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("userId")).isNotNull();
        assertThat(response.getBody().get("nickname")).isNotNull();
        assertThat(response.getBody().get("pending")).isEqualTo(true); // 还没 onboarding
    }

    /**
     * 查不存在的用户：返回 10001 错误码
     */
    @SuppressWarnings("unchecked")
    @Test
    void getProfile_userNotFound_shouldReturnError() {
        long fakeId = 9999999999999L;

        ResponseEntity<Map> response = rest.getForEntity(
                "/api/v1/profiles/{userId}", Map.class, fakeId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK); // 业务异常走 200 + code
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo(10001);
    }

    /**
     * 修改资料：改昵称和简介，验证改后能查到新值
     */
    @SuppressWarnings("unchecked")
    @Test
    void updateProfile_shouldUpdateFields() {
        // 先改资料
        rest.put("/api/v1/profiles/{userId}", Map.of(
                "nickname", "新昵称",
                "bio", "这是一段简介"
        ), userId);

        // 再查回来验证
        ResponseEntity<Map> response = rest.getForEntity(
                "/api/v1/profiles/{userId}", Map.class, userId);

        assertThat(response.getBody().get("nickname")).isEqualTo("新昵称");
        assertThat(response.getBody().get("bio")).isEqualTo("这是一段简介");
    }

    /**
     * Onboarding：补齐资料后 pending 变为 false
     */
    @SuppressWarnings("unchecked")
    @Test
    void onboarding_shouldSetPendingFalse() {
        rest.postForEntity(
                "/api/v1/profiles/{userId}/onboarding",
                Map.of(
                        "nickname", "测试用户",
                        "gender", 1,
                        "birthday", "2000-01-01",
                        "age", 25,
                        "bio", "hello",
                        "occupation", "工程师",
                        "education", "本科",
                        "height", 175,
                        "preferredLocation", "北京"
                ),
                Map.class, userId);

        // 查资料验证 pending=false
        ResponseEntity<Map> response = rest.getForEntity(
                "/api/v1/profiles/{userId}", Map.class, userId);

        assertThat(response.getBody().get("pending")).isEqualTo(false);
        assertThat(response.getBody().get("gender")).isEqualTo(1);
        assertThat(response.getBody().get("occupation")).isEqualTo("工程师");
    }

    /**
     * 封禁检查：正常用户返回 banned=false
     */
    @SuppressWarnings("unchecked")
    @Test
    void banCheck_normalUser_shouldReturnNotBanned() {
        ResponseEntity<Map> response = rest.getForEntity(
                "/api/v1/ban/{userId}", Map.class, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("banned")).isEqualTo(false);
    }

    /**
     * 封禁检查：不存在的用户也返回 banned=false（不阻止登录）
     */
    @SuppressWarnings("unchecked")
    @Test
    void banCheck_nonExistUser_shouldReturnNotBanned() {
        ResponseEntity<Map> response = rest.getForEntity(
                "/api/v1/ban/{userId}", Map.class, 9999999999999L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("banned")).isEqualTo(false);
    }

    /**
     * 兴趣标签：全量替换后能查到新标签
     */
    @SuppressWarnings("unchecked")
    @Test
    void replaceInterest_shouldReplaceAll() {
        // 替换为两个标签
        rest.put("/api/v1/interests/replace",
                Map.of("items", List.of(
                        Map.of("type", "TEXT", "content", "跑步", "sortOrder", 1),
                        Map.of("type", "TEXT", "content", "阅读", "sortOrder", 2)
                )),
                userId);

        // 查资料验证兴趣
        ResponseEntity<Map> response = rest.getForEntity(
                "/api/v1/profiles/{userId}", Map.class, userId);

        assertThat(response.getBody()).isNotNull();
        List<Map<String, Object>> interests =
                (List<Map<String, Object>>) response.getBody().get("interests");
        assertThat(interests).hasSize(2);
        assertThat(interests.get(0).get("content")).isEqualTo("跑步");
    }

    /**
     * 批量查资料：一次查多个用户
     */
    @SuppressWarnings("unchecked")
    @Test
    void batchGetProfile_shouldReturnMultiple() {
        // 先多注册几个用户
        ResponseEntity<Map> u2 = rest.postForEntity(
                "/api/v1/identity/device",
                Map.of("deviceId", "batch-test-device-2", "platform", "ios"),
                Map.class);
        Long userId2 = ((Number) u2.getBody().get("userId")).longValue();

        // 批量查
        ResponseEntity<List> response = rest.postForEntity(
                "/api/v1/profiles/batch",
                Map.of("userIds", List.of(userId, userId2)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    /**
     * 批量查超过 200 个：返回 10401 错误码
     */
    @SuppressWarnings("unchecked")
    @Test
    void batchGetProfile_exceedLimit_shouldReturnError() {
        // 构造 201 个 ID
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 201)
                .boxed().collect(java.util.stream.Collectors.toList());

        ResponseEntity<Map> response = rest.postForEntity(
                "/api/v1/profiles/batch",
                Map.of("userIds", ids),
                Map.class);

        assertThat(response.getBody().get("code")).isEqualTo(10401);
    }
}
