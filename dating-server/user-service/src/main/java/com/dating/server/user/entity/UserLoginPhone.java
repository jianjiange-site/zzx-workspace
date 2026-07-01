package com.dating.server.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 手机号 ↔ 用户绑定表 user_login_phone 实体
 * 每个 (phoneE164, appName) 唯一绑定一个 userId
 * 设计文档 §5.3：手机号显式入口，不支持多键优先级混合
 */
@Data
@TableName("user_login_phone")
public class UserLoginPhone {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的用户 ID */
    private Long userId;

    /** E.164 格式手机号（如 +8613800000001） */
    private String phoneE164;

    /** 应用标识，同一个手机号在不同 App 可以各自绑定 */
    private String appName;

    /** 手机号验证时间 */
    private Instant verifiedAt;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
}
