package com.dating.server.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.user.entity.UserThirdPartyRegistration;
import com.dating.server.user.mapper.UserThirdPartyRegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 第三方账号绑定 Manager
 * 设计文档 §5.3：Google/Apple/微信登录的身份解析
 */
@Component
@RequiredArgsConstructor
public class UserThirdPartyManager {

    private final UserThirdPartyRegistrationMapper mapper;

    /**
     * 查找有效的第三方授权绑定
     */
    public UserThirdPartyRegistration findActive(String platform, String thirdPartyUserId) {
        return mapper.selectOne(
                new LambdaQueryWrapper<UserThirdPartyRegistration>()
                        .eq(UserThirdPartyRegistration::getPlatform, platform)
                        .eq(UserThirdPartyRegistration::getThirdPartyUserId, thirdPartyUserId)
        );
    }

    /** 插入第三方账号绑定 */
    public void insert(UserThirdPartyRegistration record) {
        mapper.insert(record);
    }

    /** 按 userId 删除绑定 */
    public void deleteByUserId(Long userId) {
        mapper.delete(
                new LambdaQueryWrapper<UserThirdPartyRegistration>()
                        .eq(UserThirdPartyRegistration::getUserId, userId)
        );
    }
}
