package com.dating.server.gateway.auth;

import com.dating.server.gateway.common.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 鉴权过滤器。除了 /api/v1/auth/*（登录注册）外所有请求都需要 Bearer token。
 * 解析出的 userId 放入 request attribute，供下游 controller 直接从 request 取。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException {
        try {
            String token = extractToken(request);
            if (token != null) {
                // 解析失败会抛 JwtException，直接走 catch 返回 401
                Long userId = jwtUtil.getUserId(token);
                request.setAttribute("userId", userId);
            }
            // token 为 null 时不拦截——简化版让业务层抛 NPE 走全局异常
            chain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            writeError(response, 401, "token expired");
        } catch (JwtException e) {
            writeError(response, 401, "invalid token");
        } catch (Exception e) {
            writeError(response, 500, "internal error");
        }
    }

    /** 登录/注册路径不需要 token */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/");
    }

    /** 从 Authorization: Bearer xxx 中提取 token 字符串 */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    /** 写 401 JSON 响应，格式与 R<T> 一致，前端统一处理 */
    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), R.unauthorized(message));
    }
}
