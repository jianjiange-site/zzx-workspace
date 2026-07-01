package com.dating.server.user.dto;

import lombok.Getter;

/**
 * 身份解析结果 DTO
 * 被 Service 返回，最终由 gRPC impl 层转为 proto message
 *
 * @param userId  解析到的用户 ID
 * @param pending true=刚创建的占位用户（还没补齐资料），false=已有用户
 */
@Getter
public class IdentityResolveResult {

    private final Long userId;
    private final boolean pending;

    private IdentityResolveResult(Long userId, boolean pending) {
        this.userId = userId;
        this.pending = pending;
    }

    /** 已有用户：直接返回 userId，pending=false */
    public static IdentityResolveResult existing(Long userId) {
        return new IdentityResolveResult(userId, false);
    }

    /** 新创建的占位用户：pending=true */
    public static IdentityResolveResult pending(Long userId) {
        return new IdentityResolveResult(userId, true);
    }
}
