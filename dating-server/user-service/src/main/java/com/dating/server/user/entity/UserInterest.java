package com.dating.server.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 用户兴趣标签表 user_interest 实体
 * IMAGE 类型存图片 object_key，TEXT 类型存文字内容
 * 设计文档 §5.6：全量替换语义，每次 ReplaceUserInterests 对整个用户 DELETE + INSERT
 */
@Data
@TableName("user_interest")
public class UserInterest {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的用户 ID */
    private Long userId;

    /** 兴趣类型：IMAGE（图片标签）/ TEXT（文字标签） */
    private String type;

    /** 图片 object_key（type=IMAGE 时使用） */
    private String picKey;

    /** 文字内容（type=TEXT 时使用，最长 128） */
    private String content;

    /** 排序序号，升序排列 */
    private Integer sortOrder;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
}
