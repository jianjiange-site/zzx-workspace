package com.dating.server.user.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户资料 VO（GetProfile 返回）
 * 包含主资料 + 头像 key + 兴趣标签
 * 注意：occupation 是 UI 字段名，对应 DB 的 profession
 */
@Data
public class UserProfileVO {

    private Long userId;

    /** 主资料 */
    private String nickname;
    private Integer gender;
    private LocalDate birthday;
    private Integer age;
    private String bio;
    private String occupation;      // UI 命名，对应 DB profession
    private String education;
    private Integer height;
    private String preferredLocation;
    private Boolean pending;

    /** 头像 object_key（App 侧自拼 CDN URL） */
    private String originalKey;
    private String minKey;
    private String midKey;

    /** 兴趣标签 */
    private List<UserInterestVO> interests;
}
