package com.dating.server.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dating.server.user.entity.UserLoginPhone;

/**
 * 手机号绑定 Mapper
 * 按 (phoneE164, appName) 查找是核心查询
 */
public interface UserLoginPhoneMapper extends BaseMapper<UserLoginPhone> {
}
