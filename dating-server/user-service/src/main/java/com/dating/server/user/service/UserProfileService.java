package com.dating.server.user.service;

import com.dating.server.user.dto.UserProfileVO;
import com.dating.server.user.dto.UpdateProfileRequest;
import com.dating.server.user.dto.UpsertOnboardingRequest;

import java.util.List;

/**
 * 用户资料服务接口
 * 设计文档 §5.2：资料读写 + 兴趣 + 头像信息（不含 presign/confirm）
 */
public interface UserProfileService {

    /** 单用户资料读取（含兴趣 + 头像 key） */
    UserProfileVO getProfile(Long userId);

    /**
     * 批量资料读取（≤200/次）
     * Redis MGET → miss 的 ID 集合 → SELECT WHERE IN → 回填缓存
     */
    List<UserProfileVO> batchGetProfile(List<Long> userIds);

    /**
     * 更新资料（动态 SET，null 字段不更新）
     * 只处理 MVP 的 7 个字段，avatar/tags 走专用接口
     */
    void updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * Onboarding 一次性写入
     * 设置 gender/birthday 等仅入口字段 + 清 pending 标记
     */
    void upsertOnboarding(Long userId, UpsertOnboardingRequest request);
}
