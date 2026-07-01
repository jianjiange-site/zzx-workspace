package com.dating.server.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置
 *
 * 配置 RedisTemplate 的序列化方式，让存进去的值可读（JSON 格式）。
 * 如果不配，Spring 默认用 JDK 序列化，存进 Redis 的是乱码二进制。
 *
 * StringRedisTemplate 可以直接 @Autowired 使用，key/value 都是字符串。
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
