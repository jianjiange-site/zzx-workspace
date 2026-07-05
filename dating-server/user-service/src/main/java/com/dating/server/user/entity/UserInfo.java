package com.dating.server.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 用户主资料表 user_info 对应的实体类
 * 字段映射规则：DB 下划线 → Java 驼峰（mybatis-plus 配置了 map-underscore-to-camel-case: true）
 */
@Data
@TableName(value = "user_info", autoResultMap = true)
public class UserInfo {

    /** 主键，ASSIGN_ID = 雪花算法分布式 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 应用标识，区分不同 App（如 zzx-dating） */
    private String appName;

    /** 昵称，最长 64 字符 */
    private String nickname;

    /** 性别：0=未知 1=男 2=女 */
    private Integer gender;

    /** 生日 */
    private LocalDate birthday;

    /** 年龄（冗余字段，可由生日计算） */
    private Integer age;

    /** 个人简介，最长 500 字 */
    private String bio;

    /** 职业（UI 上叫 Occupation，DB 用 profession 命名） */
    private String profession;

    /** 学历 */
    private String education;

    /** 身高，单位 cm */
    private Integer height;

    /** 偏好位置，中文字符串，如"北京市"，最长 128 */
    private String preferredLocation;

    /**
     * 头像 JSON 结构体，存储对象存储的 object_key
     * JSONB 类型，结构：{originalKey, minKey, midKey}
     * JacksonTypeHandler 负责自动序列化/反序列化
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String customAvatar;

    /** 是否占位用户：true=刚注册还没补齐资料，false=已完成 onboarding */
    private Boolean pending;

    /**
     * 监管状态：0=正常 2=封禁 5=暂停
     * 封禁判定固定 IN (2, 5)
     */
    private Integer regulationStatus;

    /** 最后一次打开 App 的时间（TIMESTAMPTZ 时区感知） */
    private Instant lastOpenAt;

    /** 用户类型：1=BH(真人) 2=DH(数字人) */
    private Integer userType;

    /** 颜值分 0-100 */
    private Integer beautyScore;

    /** 人种 */
    private String race;

    /** 纬度 */
    private Double latitude;

    /** 经度 */
    private Double longitude;

    /** 联系方式-手机号（不作登录凭证，纯联系用） */
    private String phoneNumber;

    /** 联系方式-邮箱（不作登录凭证） */
    private String email;

    /** 创建时间，MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    /** 更新时间，插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
