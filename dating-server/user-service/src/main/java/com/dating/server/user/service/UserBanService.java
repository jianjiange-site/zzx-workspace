package com.dating.server.user.service;

import com.dating.server.user.dto.BanResult;

/**
 * 封禁查询服务接口
 * 设计文档 §5.7：查 user_info.regulation_status + 运营封禁集合
 */
public interface UserBanService {

    /**
     * 查询用户是否被封禁
     *
     * @param userId 用户 ID
     * @return 封禁检查结果（banned/reason/message）
     */
    BanResult checkBan(Long userId);
}
