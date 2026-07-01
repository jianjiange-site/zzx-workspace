package com.dating.server.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.user.entity.UserLoginPhone;
import com.dating.server.user.mapper.UserLoginPhoneMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 手机号绑定 Manager
 * 设计文档 §5.3：手机号作为登录凭证的绑定管理
 */
@Component
@RequiredArgsConstructor
public class UserLoginPhoneManager {

    private final UserLoginPhoneMapper userLoginPhoneMapper;

    /**
     * 按 (phoneE164, appName) 查找绑定记录
     * 这是登录流程的核心查询，唯一约束保证了最多返回一条
     */
    public UserLoginPhone findByPhoneAndApp(String phoneE164, String appName) {
        return userLoginPhoneMapper.selectOne(
                new LambdaQueryWrapper<UserLoginPhone>()
                        .eq(UserLoginPhone::getPhoneE164, phoneE164)
                        .eq(UserLoginPhone::getAppName, appName)
        );
    }

    /** 插入手机号绑定 */
    public void insert(UserLoginPhone record) {
        userLoginPhoneMapper.insert(record);
    }

    /** 删除绑定记录 */
    public void deleteByUserId(Long userId) {
        userLoginPhoneMapper.delete(
                new LambdaQueryWrapper<UserLoginPhone>()
                        .eq(UserLoginPhone::getUserId, userId)
        );
    }
}
