package com.dating.server.user.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Onboarding 请求 DTO
 * 设计文档 §5.2：一次性写入完整资料 + 默认头像
 * gender / birthday 等编辑页改不到的字段的唯一写入入口
 */
@Data
public class UpsertOnboardingRequest {

    private String nickname;
    private Integer gender;         // 仅在此入口可写
    private LocalDate birthday;     // 仅在此入口可写
    private Integer age;
    private String bio;
    private String occupation;
    private String education;
    private Integer height;
    private String preferredLocation;
}
