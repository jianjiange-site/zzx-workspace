package com.dating.server.user.service;

import com.dating.server.user.dto.IdentityResolveResult;

/**
 * 身份解析服务接口
 * 设计文档 §5.3：负责用户身份解析 + placeholder 创建
 */
public interface UserIdentityService {

    /**
     * 手机号登录：找现有用户或创建占位用户，更新 last_open_at
     *
     * @param phoneE164 已规范化的 E.164 手机号（由调用方负责）
     * @param appName   应用标识
     * @return 身份解析结果（userId + pending 标记）
     */
    IdentityResolveResult resolveOrCreateByPhone(String phoneE164, String appName);

    /**
     * 第三方登录：Google / Apple / 微信
     *
     * @param platform         第三方平台
     * @param thirdPartyUserId 第三方用户 ID
     * @param appName          应用标识
     * @param googleEmail      Google 邮箱（仅 platform=google 时传递）
     */
    IdentityResolveResult resolveOrCreateByThirdParty(String platform, String thirdPartyUserId,
                                                      String appName, String googleEmail);

    /**
     * 设备快速登录：用 deviceId 找用户或创建占位
     *
     * @param deviceId 设备 ID
     * @param platform 设备平台 ios/android
     * @param appName  应用标识
     */
    IdentityResolveResult resolveOrCreateByDevice(String deviceId, String platform, String appName);
}
