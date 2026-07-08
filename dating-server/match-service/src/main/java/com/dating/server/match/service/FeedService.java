package com.dating.server.match.service;

import java.util.List;

/**
 * Feed 服务 —— 卡片队列消费
 *
 * PRD 4.3 定义的 GetTodayFeed：
 *   - LPOP 从 Redis LIST 消费
 *   - 空队列触发 ColdStartService.buildAndPush 重建
 *   - 二次过滤已 swipe 卡片
 *   - 调 user-service.batchGetProfile 拼装 CardVO
 */
public interface FeedService {

    /**
     * 获取今日 feed 卡片
     *
     * @param userId 用户 ID
     * @param count  期望获取的卡片数（移动端固定 5）
     * @return FeedResult 包含卡片列表 + 是否耗尽
     */
    FeedResult getTodayFeed(Long userId, int count);

    /** Feed 返回结果 */
    record FeedResult(List<CardVO> cards, boolean exhausted) {}

    /** 卡片 VO（不含 profile 字段时为 RAW 模式，由下游 enrich） */
    record CardVO(
            Long targetUserId,
            int targetUserType,
            String nickname,
            Integer age,
            List<String> photoKeys,
            String bio,
            Double distanceKm
    ) {
        /** 简化构造（仅 IDs，下游 enrich） */
        public static CardVO raw(Long targetUserId, int targetUserType) {
            return new CardVO(targetUserId, targetUserType, null, null, null, null, null);
        }
    }
}
