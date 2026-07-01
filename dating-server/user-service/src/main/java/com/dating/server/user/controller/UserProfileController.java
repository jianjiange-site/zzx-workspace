package com.dating.server.user.controller;

import com.dating.server.user.dto.UpdateProfileRequest;
import com.dating.server.user.dto.UpsertOnboardingRequest;
import com.dating.server.user.dto.UserProfileVO;
import com.dating.server.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户资料读写：单个/批量查询、资料编辑、Onboarding 初始化。
 * 所有接口走 cache aside 模式（Redis → DB）。
 */
@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * 查询单个用户资料：优先返回 Redis 缓存，未命中则回源 DB 并回填缓存。
     * 包含主资料、兴趣标签、头像 key（不签 URL，前端自拼 CDN 地址）。
     *
     * @param userId 业务主键，必须 > 0
     * @return 用户档案 VO（含 nickname/age/photoKeys/bio/occupation/education/height/interests 等）
     * @throws com.dating.server.user.exception.BizException 用户不存在或已逻辑删除时抛出
     */
    @GetMapping("/{userId}")
    public UserProfileVO getProfile(@PathVariable Long userId) {
        return userProfileService.getProfile(userId);
    }

    /**
     * 批量查询用户资料：Redis MGET 批量命中 → miss 的 ID 回源 DB → 回填缓存。单次最多 200 个 ID。
     * 适用于 Feed 列表、匹配卡片等需要批量展示用户资料的场景。
     *
     * @param userIds 用户 ID 列表，长度 ≤ 200
     * @return 用户档案 VO 列表；不存在的用户不会出现在结果中（不抛异常）
     */
    @PostMapping("/batch")
    public List<UserProfileVO> batchGetProfile(@RequestBody List<Long> userIds) {
        return userProfileService.batchGetProfile(userIds);
    }

    /**
     * 更新用户资料（MVP 字段）：动态 SET，跳过 null 字段。可修改的字段包括昵称/年龄/位置/Bio/职业/学历/身高。
     * 头像和兴趣标签走专用接口（avatars/* / interests/replace），不在此处修改。
     *
     * @param userId  目标用户 ID，必须与当前登录用户一致
     * @param request 待更新的字段，不传的字段保持不变
     */
    @PutMapping("/{userId}")
    public void updateProfile(@PathVariable Long userId,
                              @RequestBody @Valid UpdateProfileRequest request) {
        userProfileService.updateProfile(userId, request);
    }

    /**
     * Onboarding 一次性写入完整资料：仅新用户注册后首次调用。
     * 写入 gender / birthday / nickname 等字段，设置 pending=false 完成初始化。
     * 调用方保证该接口在用户生命周期中只调一次。
     *
     * @param userId  目标用户 ID
     * @param request 完整资料，含 gender/birthday/nickname 等必填字段
     */
    @PostMapping("/{userId}/onboarding")
    public void upsertOnboarding(@PathVariable Long userId,
                                 @RequestBody @Valid UpsertOnboardingRequest request) {
        userProfileService.upsertOnboarding(userId, request);
    }
}
