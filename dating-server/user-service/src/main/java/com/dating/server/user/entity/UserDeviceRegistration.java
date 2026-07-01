package com.dating.server.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 设备 ↔ 用户绑定表 user_device_registration 实体
 * 用于快速登录（无短信/无三方），直接拿设备 ID 关联用户
 * 设计文档 §5.3：同一 userId 可同时持有 phone + 第三方 + device 三种绑定
 */
@Data
@TableName("user_device_registration")
public class UserDeviceRegistration {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的用户 ID */
    private Long userId;

    /** 设备 ID（iOS IDFV / Android SSAID） */
    private String deviceId;

    /** 设备平台：ios / android */
    private String platform;

    /** 应用标识 */
    private String appName;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
}
