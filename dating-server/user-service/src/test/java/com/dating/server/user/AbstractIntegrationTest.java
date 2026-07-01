package com.dating.server.user;

import com.dating.server.user.service.ObjectStorage;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 集成测试基类
 *
 * 自动启动 PostgreSQL + Redis 容器，注入 TestRestTemplate。
 * 子类只管写 HTTP 请求和断言，不用管容器生命周期。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // ========== PostgreSQL 容器 ==========
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dating_test")
            .withUsername("dating")
            .withPassword("dating123");

    // ========== Redis 容器 ==========
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", "redis123");

    /**
     * 把 Testcontainers 的动态端口注入 Spring 配置
     * 这样应用启动时就连到容器里的 PG 和 Redis，而不是配置文件的地址。
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "redis123");
    }

    // ========== Mock ObjectStorage（测试不依赖 MinIO） ==========
    @Autowired
    protected TestRestTemplate rest;

    /**
     * 测试配置：用 Mock 替换真实的 S3 对象存储，
     * 这样跑测试不需要起 MinIO 容器。
     */
    @TestConfiguration
    static class TestObjectStorageMock {
        @Bean
        @Primary
        public ObjectStorage objectStorage() {
            ObjectStorage mock = mock(ObjectStorage.class);
            // presignedPutUrl 默认返回一个假 URL
            when(mock.presignedPutUrl(any(), any())).thenReturn("http://test-presigned-url");
            // doesObjectExist 默认返回 true（假设文件已上传）
            when(mock.doesObjectExist(any())).thenReturn(true);
            return mock;
        }
    }
}
