package com.dating.server.match.service.impl;

import com.dating.proto.user.UserProfile;
import com.dating.server.match.client.UserServiceClient;
import com.dating.server.match.constant.CacheKeys;
import com.dating.server.match.service.ColdStartService;
import com.dating.server.match.service.FeedService;
import com.dating.server.match.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Feed 服务实现 —— Redis LIST LPOP 消费模型
 *
 * PRD 4.3 流程图（GetTodayFeed）:
 *   1. 检查当日卡片配额是否耗尽 → 是 → 返回 exhausted=true
 *   2. need = min(count, card_limit - cards_used)
 *   3. while result.size < need:
 *        a. LPOP 一批
 *        b. LIST 为空 → ColdStartService.buildAndPush 重建 → 继续 LPOP
 *        c. 二次过滤：SMISMEMBER match:swiped:<userId>，丢弃已 swipe 的
 *   4. 调 user-service.batchGetProfile 拼装 CardVO(nickname/age/photo_keys/distance)
 *   5. 返回卡片列表
 *
 * Redis 数据结构：
 *   - match:feed:<userId>  LIST  元素 = "<targetUserId>:<userType>"
 *   - match:swiped:<userId> SET  该用户所有已 swipe 过的 target
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final StringRedisTemplate stringRedisTemplate;
    private final QuotaService quotaService;
    private final ColdStartService coldStartService;
    private final UserServiceClient userServiceClient;

    /** PRD: 移动端固定每次拉 5 张，但 LPOP 可多弹一些减少重建次数 */
    private static final int LPOP_BATCH = 5;
    /** 队列重建后最大重试次数 */
    private static final int MAX_REBUILD_RETRIES = 2;
    /** PRD: count <= 20 */
    private static final int MAX_FEED_COUNT = 20;

    @Override
    public FeedResult getTodayFeed(Long userId, int count) {
        // ── 第1步：检查当日卡片配额 ──
        count = Math.min(count, MAX_FEED_COUNT);
        QuotaService.QuotaInfo quota = quotaService.getQuota(userId);
        String tier = quotaService.getUserTier(userId);

        int cardsUsed = quota.cardsUsed();
        int cardLimit = quotaService.getCardLimit(tier);
        int cardsRemaining = cardLimit - cardsUsed;

        if (cardsRemaining <= 0) {
            log.debug("卡片配额已耗尽: userId={}, used={}/{}", userId, cardsUsed, cardLimit);
            return new FeedResult(Collections.emptyList(), true);
        }

        // ── 第2步：计算需要拉取的数量（不超过配额剩余） ──
        int need = Math.min(count, cardsRemaining);
        // 暂存 LPOP 原始结果用于二次过滤
        List<RawCard> rawCards = new ArrayList<>(MAX_FEED_COUNT);
        int rebuildCount = 0;

        // ── 第3步：while 循环 LPOP 凑足 need ──
        while (rawCards.size() < need) {
            int batchSize = Math.min(need - rawCards.size(), LPOP_BATCH);

            // 3a. 批量 LPOP
            List<RawCard> batch = lpopCards(userId, batchSize);

            // 3b. LIST 为空 → 实时重建
            if (batch.isEmpty()) {
                if (rebuildCount >= MAX_REBUILD_RETRIES) {
                    log.debug("队列重建已达最大次数, userId={}", userId);
                    break;
                }
                coldStartService.buildAndPush(userId);
                rebuildCount++;
                continue;
            }

            // 3c. 二次过滤：排除已 swipe 的 target
            for (RawCard card : batch) {
                if (isSwiped(userId, card.targetUserId)) {
                    continue; // 已 swipe，丢弃
                }
                rawCards.add(card);
                if (rawCards.size() >= need) break;
            }
        }

        if (rawCards.isEmpty()) {
            log.debug("Feed 无可用卡片: userId={}", userId);
            return new FeedResult(Collections.emptyList(), false);
        }

        // ── 第4步：调 user-service.batchGetProfile 拼装完整 CardVO ──
        List<Long> userIds = rawCards.stream()
                .map(c -> c.targetUserId)
                .collect(Collectors.toList());
        Map<Long, UserProfile> profiles = userServiceClient.batchGetProfiles(userIds);

        List<CardVO> enriched = new ArrayList<>(rawCards.size());
        for (RawCard raw : rawCards) {
            UserProfile p = profiles.get(raw.targetUserId);
            if (p != null) {
                // photo_keys: 用 avatar_min/avatar_mid/avatar_original 去空
                List<String> photos = new ArrayList<>(3);
                if (p.getAvatarMin() != null && !p.getAvatarMin().isEmpty()) photos.add(p.getAvatarMin());
                if (p.getAvatarMid() != null && !p.getAvatarMid().isEmpty()) photos.add(p.getAvatarMid());
                if (p.getAvatarOriginal() != null && !p.getAvatarOriginal().isEmpty()) photos.add(p.getAvatarOriginal());

                enriched.add(new CardVO(
                        raw.targetUserId, raw.targetUserType,
                        p.getNickname(), p.getAge(),
                        photos, p.getBio(),
                        null)); // distanceKm: UserProfile 不含距离，由前端根据 targetUserType 决定展示
            } else {
                // 降级：profile 查不到也返回，由前端处理匿名
                enriched.add(CardVO.raw(raw.targetUserId, raw.targetUserType));
            }
        }

        log.debug("Feed 消费: userId={}, need={}, actual={}, exhausted={}",
                userId, need, enriched.size(), false);
        return new FeedResult(enriched, false);
    }

    // ═══════════════════════════════════════
    //  内部方法
    // ═══════════════════════════════════════

    /**
     * 从 Redis LIST 批量 LPOP
     *
     * Redis 7.0+ 支持 ListOperations.leftPop(key, count) 批量弹出。
     */
    private List<RawCard> lpopCards(Long userId, int count) {
        String feedKey = CacheKeys.feed(userId);
        List<String> elements = stringRedisTemplate.opsForList().leftPop(feedKey, count);
        if (elements == null || elements.isEmpty()) return Collections.emptyList();

        List<RawCard> cards = new ArrayList<>(elements.size());
        for (String el : elements) {
            String[] parts = el.split(":");
            if (parts.length < 2) {
                log.warn("Feed 元素格式异常: {}", el);
                continue;
            }
            try {
                cards.add(new RawCard(Long.parseLong(parts[0]), Integer.parseInt(parts[1])));
            } catch (NumberFormatException e) {
                log.warn("Feed 元素解析失败: {}", el, e);
            }
        }
        return cards;
    }

    /**
     * 检查 target 是否已被当前用户 swipe 过
     *
     * 用 Redis SMISMEMBER 批量查（通过单个 isMember 实现，batch 总 ≤5）。
     */
    private boolean isSwiped(Long userId, Long targetUserId) {
        Boolean member = stringRedisTemplate.opsForSet()
                .isMember(CacheKeys.swiped(userId), String.valueOf(targetUserId));
        return Boolean.TRUE.equals(member);
    }

    /** LPOP 原始卡片（未 enrich） */
    private record RawCard(Long targetUserId, int targetUserType) {}
}
