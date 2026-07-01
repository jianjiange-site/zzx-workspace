package com.dating.server.user.controller;

import com.dating.server.user.dto.ReplaceInterestItem;
import com.dating.server.user.service.UserInterestService;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 兴趣标签管理：全量替换语义，读取并入 UserProfileController.getProfile。
 */
@RestController
@RequestMapping("/api/v1/interests")
@RequiredArgsConstructor
public class UserInterestController {

    private final UserInterestService userInterestService;

    /**
     * 全量替换用户兴趣标签：事务内 DELETE + INSERT 批量，然后清除 Redis 缓存。
     * 图片标签 ≤ 9 条，文字标签 ≤ 50 条。
     *
     * @param request userId（目标用户）、items（兴趣列表，含 type/picKey/textContent）
     */
    @PutMapping("/replace")
    public void replaceInterests(@RequestBody @jakarta.validation.Valid ReplaceRequest request) {
        userInterestService.replaceUserInterests(request.getUserId(), request.getItems());
    }

    @Data
    public static class ReplaceRequest {
        @NotNull
        private Long userId;
        @NotNull
        private List<ReplaceInterestItem> items;
    }
}
