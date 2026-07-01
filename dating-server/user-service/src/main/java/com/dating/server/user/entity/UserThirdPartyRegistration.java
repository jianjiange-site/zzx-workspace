package com.dating.server.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 第三方账号 ↔ 用户绑定表 user_third_party_registration 实体
 * 用于 Google/Apple/微信等第三方登录
 * 设计文档 §5.3：第三方显式入口，不与手机号混合
 */
@Data
@TableName("user_third_party_registration")
public class UserThirdPartyRegistration {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的用户 ID */
    private Long userId;

    /** 第三方平台：google / apple / wechat 等 */
    private String platform;

    /** 第三方用户 ID（由平台返回，不变） */
    private String thirdPartyUserId;

    /** 应用标识 */
    private String appName;

    /** Google 邮箱（仅 platform=google 时非空） */
    private String googleEmail;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
}
