package com.dating.server.user.dto;

import lombok.Data;

/**
 * 资料更新请求 DTO
 * 设计文档 §5.2：MVP 暴露 7 个字段，avatar/tags 走专用 RPC
 * 字段为 null 时不更新（动态 SET），空字符串会清空
 */
@Data
public class UpdateProfileRequest {

    private String nickname;
    private Integer age;
    private String bio;
    private String occupation;      // → DB profession
    private String education;
    private Integer height;
    private String preferredLocation;
}
