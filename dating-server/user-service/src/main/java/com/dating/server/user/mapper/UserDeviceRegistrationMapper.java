package com.dating.server.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dating.server.user.entity.UserDeviceRegistration;

/**
 * 设备绑定 Mapper
 * 按 (deviceId, platform, appName) 查找
 */
public interface UserDeviceRegistrationMapper extends BaseMapper<UserDeviceRegistration> {
}
