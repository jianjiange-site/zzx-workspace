package com.dating.server.user.service.impl;

import com.dating.server.user.dto.ReplaceInterestItem;
import com.dating.server.user.entity.UserInterest;
import com.dating.server.user.exception.BizException;
import com.dating.server.user.exception.ErrorCodes;
import com.dating.server.user.manager.UserInterestManager;
import com.dating.server.user.service.UserInterestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 兴趣标签服务实现
 *
 * 兴趣标签的修改是「全量替换」语义：
 * 不提供单条增/删/改，每次都是把用户的所有旧标签删掉，然后插入新标签。
 *
 * 为什么这么设计？
 * - 前端每次编辑兴趣都是全量提交（所有标签一起发过来）
 * - 避免复杂的增删改逻辑，简单可靠
 *
 * 限制：图片标签最多 9 个，文字标签最多 50 个。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserInterestServiceImpl implements UserInterestService {

    private final UserInterestManager userInterestManager;

    /**
     * ===== 全量替换兴趣标签 =====
     *
     * 流程：
     *   第1步：校验数量（图片 ≤ 9，文字 ≤ 50）
     *   第2步：把前端传的 DTO 转为实体对象
     *   第3步：事务内删除旧标签 + 插入新标签
     *   第4步：（TODO）删除 Redis 缓存
     *
     * @param userId 用户 ID
     * @param items  新的兴趣标签列表（全量，不是增量）
     */
    @Override
    @Transactional
    public void replaceUserInterests(Long userId, List<ReplaceInterestItem> items) {

        // 第1步：统计并校验数量
        // 从列表中分别数出图片标签和文字标签各有多少个
        long imageCount = items.stream().filter(i -> "IMAGE".equals(i.getType())).count();
        long textCount = items.stream().filter(i -> "TEXT".equals(i.getType())).count();

        // 图片标签不能超过 9 个（产品需求）
        if (imageCount > 9) {
            throw new BizException(ErrorCodes.INTEREST_IMAGE_LIMIT_EXCEEDED,
                    "图片标签超过上限: " + imageCount + " > 9");
        }
        // 文字标签不能超过 50 个（产品需求）
        if (textCount > 50) {
            throw new BizException(ErrorCodes.INTEREST_TEXT_LIMIT_EXCEEDED,
                    "文字标签超过上限: " + textCount + " > 50");
        }

        // 第2步：把前端传的 ReplaceInterestItem（DTO）转为 UserInterest（实体）
        // DTO 是给前端看的格式，实体是给数据库看的格式
        List<UserInterest> interests = items.stream().map(item -> {
            UserInterest interest = new UserInterest();
            interest.setType(item.getType());           // IMAGE 或 TEXT
            interest.setPicKey(item.getPicKey());       // 图片标签的 object_key
            interest.setContent(item.getContent());     // 文字标签的内容
            // 排序序号：如果前端没传就用 0
            interest.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
            return interest;
        }).collect(Collectors.toList());

        // 第3步：执行全量替换
        // Manager 里做的是：DELETE FROM user_interest WHERE user_id = ? 然后逐条 INSERT
        // 因为有 @Transactional 事务保证，要么全部成功，要么全部回滚
        userInterestManager.replaceAll(userId, interests);

        // 第4步：删除 Redis 缓存（兴趣变了，旧缓存要清掉）
        userInterestManager.evictInterestCache(userId);

        log.info("兴趣标签已替换: userId={}, 图片={}个, 文字={}个",
                userId, imageCount, textCount);
    }
}
