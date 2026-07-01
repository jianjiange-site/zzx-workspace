package com.dating.server.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dating.server.user.entity.UserThirdPartyRegistration;

/**
 * 第三方账号绑定 Mapper
 * 按 (platform, thirdPartyUserId) 查找
 */
public interface UserThirdPartyRegistrationMapper extends BaseMapper<UserThirdPartyRegistration> {
}
