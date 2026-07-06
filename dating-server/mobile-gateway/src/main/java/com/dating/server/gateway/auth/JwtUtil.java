package com.dating.server.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * HMAC-SHA256 JWT 签发/验签工具。
 * <p>
 * 生产环境应使用 RS256（非对称），密钥对由统一认证中心管理。
 * 本项目简化用 HMAC（对称），优点是省去密钥对分发，但意味着 gateway 和 issuer 是同一个。
 * 面试时可以说——"这里用 HMAC 是为了快速迭代，上生产前要换成 RS256，密钥对放到 KMS"。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** 签发 token：oauth2 标准的 sub + 自定义 userId claim */
    public String generateToken(Long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userId", userId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    /** 验签成功返回 claims，失败抛 JwtException 子类（由过滤器统一处理） */
    public Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 token 提取 userId，等价于 verify().get("userId") */
    public Long getUserId(String token) {
        return verify(token).get("userId", Long.class);
    }
}
