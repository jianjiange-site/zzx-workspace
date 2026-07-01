package com.dating.server.user.service.impl;

import com.dating.server.user.dto.UserProfileVO;
import com.dating.server.user.dto.UserInterestVO;
import com.dating.server.user.dto.UpdateProfileRequest;
import com.dating.server.user.dto.UpsertOnboardingRequest;
import com.dating.server.user.entity.UserInfo;
import com.dating.server.user.entity.UserInterest;
import com.dating.server.user.exception.BizException;
import com.dating.server.user.exception.ErrorCodes;
import com.dating.server.user.manager.UserInfoManager;
import com.dating.server.user.manager.UserInterestManager;
import com.dating.server.user.service.UserProfileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

/**
 * 用户资料服务实现
 *
 * 包含三个核心功能：
 * 1. 读资料（单个查 + 批量查）
 * 2. 改资料（用户主动编辑）
 * 3. Onboarding（新用户第一次填完整资料）
 *
 * cache aside 模式：读走缓存（Manager 内实现），写先写 DB 再删缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserInfoManager userInfoManager;      // 用户主表操作
    private final UserInterestManager userInterestManager; // 兴趣表操作
    private final ObjectMapper objectMapper;             // Jackson JSON 工具，用来解析/生成 JSON

    /**
     * ===== 查询单个用户的资料 =====
     *
     * 流程：
     * 第1步：查 Redis 缓存 user:profile:{userId}（Manager 内实现）
     *   - 缓存命中 → 直接返回，不走数据库
     *   - 缓存未命中 → 查 DB → 回填缓存（TTL 24小时）
     * 第2步：查 Redis 缓存 user:interest:{userId}（Manager 内实现）
     * 第3步：拼装 VO 返回
     *
     * @param userId 要查的用户 ID
     * @return UserProfileVO 包含上面所有信息
     */
    @Override
    public UserProfileVO getProfile(Long userId) {

        // === 第1步：查用户资料（Manager 内走缓存） ===
        // getCachedById 会尝试先读 Redis，没命中再查 DB
        UserInfo user = userInfoManager.getCachedById(userId);
        if (user == null) {
            throw new BizException(ErrorCodes.USER_NOT_FOUND, "用户不存在: " + userId);
        }

        // === 第2步：查兴趣标签（Manager 内走缓存） ===
        List<UserInterest> interests = userInterestManager.getCachedByUserId(userId);

        // === 第3步：拼装 VO 返回 ===
        return toProfileVO(user, interests);
    }

    /**
     * ===== 批量查询用户资料 =====
     *
     * 一次查最多 200 个用户的资料。
     * 用于首页推荐列表、匹配列表等场景（一次展示多个用户）。
     *
     * （TODO）将来流程：
     * 第0步：Redis MGET 批量查缓存，没命中的 ID 集合再去 DB 查
     * 第1步：查到的数据批量回填 Redis（pipelined HMSET）
     *
     * @param userIds 用户 ID 列表，最多 200 个
     * @return 用户资料列表
     */
    @Override
    public List<UserProfileVO> batchGetProfile(List<Long> userIds) {

        // 参数校验：空列表 → 返回空
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 参数校验：超过 200 个 → 抛异常，错误码 10401
        if (userIds.size() > 200) {
            throw new BizException(ErrorCodes.BATCH_SIZE_EXCEEDED, "批量查询不能超过200个");
        }

        // === 第1步：批量查用户表 ===
        // WHERE id IN (id1, id2, ...) 一次 SQL 查回所有用户
        List<UserInfo> users = userInfoManager.getByIds(userIds);
        if (users.isEmpty()) {
            return Collections.emptyList();
        }

        // === 第2步：批量查兴趣（一次 IN 查询，避免 N+1） ===
        List<Long> ids = users.stream().map(UserInfo::getId).collect(Collectors.toList());
        List<UserInterest> allInterests = userInterestManager.getByUserIds(ids);

        // === 第3步：按 userId 分组 ===
        Map<Long, List<UserInterest>> interestMap = allInterests.stream()
                .collect(groupingBy(UserInterest::getUserId));

        // === 第4步：拼装 VO ===
        return users.stream()
                .map(u -> toProfileVO(u, interestMap.getOrDefault(u.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    /**
     * ===== 用户编辑资料 =====
     *
     * 对应 App 的「编辑个人资料」页面。
     * 设计文档 §5.2：只暴露 7 个可编辑字段（昵称、年龄、简介、职业、学历、身高、位置）
     * 头像和兴趣标签有专用的接口，不走这里。
     *
     * 关键设计：
     * - 只更新非 null 的字段（前端传了什么就改什么）
     * - nickname 做了 trim() 去前后空格
     *
     * @param userId  用户 ID
     * @param request 客户端传过来的要修改的字段（为 null 的字段不修改）
     */
    @Override
    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {

        // 第1步：检查用户是否存在
        UserInfo user = userInfoManager.getById(userId);
        if (user == null) {
            throw new BizException(ErrorCodes.USER_NOT_FOUND, "用户不存在: " + userId);
        }

        // === 第2步：动态 SET ===
        // 创建一个只包含要更新字段的 UserInfo 对象
        // 注意：这里是 new 了一个新对象，不是直接用查出来的 user 对象修改
        // 因为 MyBatis-Plus 的 updateById 会更新所有非 null 字段
        UserInfo update = new UserInfo();
        update.setId(userId);  // 设置主键，告诉 MyBatis 要更新哪条记录

        // 逐一判断：前端传了这个字段才更新，没传（null）就跳过
        if (request.getNickname() != null) {
            update.setNickname(request.getNickname().trim());  // 去前后空格
        }
        if (request.getAge() != null) {
            update.setAge(request.getAge());
        }
        if (request.getBio() != null) {
            update.setBio(request.getBio());
        }
        if (request.getOccupation() != null) {
            // 注意：UI 叫 occupation，数据库字段叫 profession
            // 前端传 occupation，我们转成 profession 存库
            update.setProfession(request.getOccupation());
        }
        if (request.getEducation() != null) {
            update.setEducation(request.getEducation());
        }
        if (request.getHeight() != null) {
            update.setHeight(request.getHeight());
        }
        if (request.getPreferredLocation() != null) {
            update.setPreferredLocation(request.getPreferredLocation());
        }

        // 第3步：执行 UPDATE
        userInfoManager.updateById(update);

        // 第4步：删除 Redis 缓存（cache aside：先写 DB，再删缓存）
        userInfoManager.evictProfileCache(userId);

        log.info("资料更新成功: userId={}", userId);
    }

    /**
     * ===== 新用户 Onboarding（首次填写资料） =====
     *
     * 用户第一次打开 App 时，需要填写完整的个人资料。
     * 这个接口和 updateProfile 的区别：
     *   - updateProfile：只改 7 个字段，不能改性别和生日
     *   - upsertOnboarding：可以写性别和生日（这两个字段注册后就不能改了）
     *   - upsertOnboarding：会把 pending 设为 false，标记为"已补齐资料"
     *
     * @param userId  用户 ID
     * @param request 完整的个人资料
     */
    @Override
    @Transactional
    public void upsertOnboarding(Long userId, UpsertOnboardingRequest request) {

        // 第1步：检查用户是否存在
        UserInfo user = userInfoManager.getById(userId);
        if (user == null) {
            throw new BizException(ErrorCodes.USER_NOT_FOUND, "用户不存在: " + userId);
        }

        // 第2步：把所有字段（含 gender/birthday）写入数据库
        UserInfo update = new UserInfo();
        update.setId(userId);
        update.setNickname(request.getNickname());
        update.setGender(request.getGender());       // 注意：这个字段只在 onboarding 时可写
        update.setBirthday(request.getBirthday());    // 这个字段只在 onboarding 时可写
        update.setAge(request.getAge());
        update.setBio(request.getBio());
        update.setProfession(request.getOccupation()); // occupation → profession 映射
        update.setEducation(request.getEducation());
        update.setHeight(request.getHeight());
        update.setPreferredLocation(request.getPreferredLocation());
        update.setPending(false);  // 关键：标记为"已补齐资料"，不再是占位用户

        // 第3步：执行 UPDATE
        userInfoManager.updateById(update);

        // 第4步：删除 Redis 缓存（资料变了，旧缓存不能用了）
        userInfoManager.evictProfileCache(userId);

        log.info("Onboarding 完成: userId={}", userId);
    }

    // ========================================================================
    // 私有方法
    // ========================================================================

    /**
     * 将数据库查出来的 UserInfo 和 UserInterest 拼成前端需要的 VO
     *
     * 主要做几件事：
     * 1. 字段复制（entity → VO）
     * 2. 字段名映射（profession → occupation）
     * 3. JSON 解析（custom_avatar 字符串 → originalKey/minKey/midKey）
     * 4. 实体列表 → VO 列表转换
     */
    private UserProfileVO toProfileVO(UserInfo user, List<UserInterest> interests) {
        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setGender(user.getGender());
        vo.setBirthday(user.getBirthday());
        vo.setAge(user.getAge());
        vo.setBio(user.getBio());
        vo.setOccupation(user.getProfession());   // 数据库叫 profession，前端叫 occupation
        vo.setEducation(user.getEducation());
        vo.setHeight(user.getHeight());
        vo.setPreferredLocation(user.getPreferredLocation());
        vo.setPending(user.getPending());

        // ===== 解析头像 JSON =====
        // custom_avatar 在数据库里是 JSONB 类型，存在 Java 里是 String
        // 结构：{"originalKey": "...", "minKey": "...", "midKey": "..."}
        // 用 Jackson 的 ObjectMapper 把 JSON 字符串解析成 Map
        if (user.getCustomAvatar() != null) {
            try {
                // 把 JSON 字符串解析成 Map<String, String>
                Map<String, String> avatarMap = objectMapper.readValue(
                        user.getCustomAvatar(), new TypeReference<Map<String, String>>() {});

                // 取出三个尺寸的 object_key
                vo.setOriginalKey(avatarMap.get("originalKey"));  // 原图
                vo.setMinKey(avatarMap.get("minKey"));            // 小图（MVP 阶段跟原图一样）
                vo.setMidKey(avatarMap.get("midKey"));            // 中图（MVP 阶段跟原图一样）
            } catch (Exception e) {
                // JSON 解析失败，只打日志不抛异常，前端拿不到头像显示默认头像就行
                log.warn("头像 JSON 解析失败, userId={}", user.getId(), e);
            }
        }

        // ===== 兴趣标签转换 =====
        if (interests != null) {
            vo.setInterests(interests.stream()
                    .map(this::toInterestVO)
                    .collect(Collectors.toList()));
        }

        return vo;
    }

    /**
     * 把 UserInterest 实体（数据库模型）转成 UserInterestVO（前端展示模型）
     * 目前字段一样，直接复制，后续扩展时在这里加逻辑
     */
    private UserInterestVO toInterestVO(UserInterest interest) {
        UserInterestVO vo = new UserInterestVO();
        vo.setType(interest.getType());         // IMAGE / TEXT
        vo.setPicKey(interest.getPicKey());     // 图片标签的 object_key
        vo.setContent(interest.getContent());   // 文字标签的内容
        vo.setSortOrder(interest.getSortOrder());// 排序序号
        return vo;
    }
}
