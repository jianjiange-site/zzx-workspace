package com.dating.server.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.user.entity.UserDeviceRegistration;
import com.dating.server.user.mapper.UserDeviceRegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 设备绑定 Manager
 * 设计文档 §5.3：快速登录（无短信/无三方）的身份解析
 */
@Component
@RequiredArgsConstructor
public class UserDeviceManager {

    private final UserDeviceRegistrationMapper mapper;

    /**
     * 查找设备绑定记录
     * 唯一约束 (deviceId, platform, appName) 保证最多返回一条
     */
    public UserDeviceRegistration findActive(String deviceId, String platform, String appName) {
        return mapper.selectOne(
                new LambdaQueryWrapper<UserDeviceRegistration>()
                        .eq(UserDeviceRegistration::getDeviceId, deviceId)
                        .eq(UserDeviceRegistration::getPlatform, platform)
                        .eq(UserDeviceRegistration::getAppName, appName)
        );
    }

    /** 插入设备绑定 */
    public void insert(UserDeviceRegistration record) {
        mapper.insert(record);
    }

    /** 按 userId 删除绑定 */
    public void deleteByUserId(Long userId) {
        mapper.delete(
                new LambdaQueryWrapper<UserDeviceRegistration>()
                        .eq(UserDeviceRegistration::getUserId, userId)
        );
    }
}
