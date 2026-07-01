package com.dating.server.user.controller;

import com.dating.server.user.dto.BanResult;
import com.dating.server.user.service.UserBanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 封禁状态查询：被 mobile-gateway 在登录流程末尾调用，也供其他服务做兜底校验。
 */
@RestController
@RequestMapping("/api/v1/ban")
@RequiredArgsConstructor
public class UserBanController {

    private final UserBanService userBanService;

    /**
     * 检查用户封禁状态：查 DB regulation_status + Redis 运营封禁 Set。
     * 结果缓存 5 分钟，gateway 在登录流程最后一步调用，命中则拒签 JWT。
     *
     * @param userId 目标用户 ID
     * @return BanResult {banned（是否被封禁）、reason（原因）、message（提示文案）}
     */
    @GetMapping("/{userId}")
    public BanResult checkBan(@PathVariable Long userId) {
        return userBanService.checkBan(userId);
    }
}
