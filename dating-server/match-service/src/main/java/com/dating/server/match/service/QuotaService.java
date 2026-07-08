package com.dating.server.match.service;

import com.dating.server.match.client.PaymentClient;
import com.dating.server.match.constant.CacheKeys;
import com.dating.server.match.exception.BizException;
import com.dating.server.match.exception.ErrorCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 每日配额服务 —— 基于 Redis HASH 的原子扣减
 *
 * 配额存储在 Redis HASH match:quota:<userId>:<yyyymmdd>：
 *   right_swipe — 当日已用右划次数
 *   cards       — 当日已用卡片数
 *   super_hi    — 当日已用 Super Hi 次数
 *
 * 订阅档位配额（PRD 3.1）：
 *   FREE:    右划 5/天, 卡片 50/天, Super Hi 0/天
 *   WEEKLY:  右划 10/天, 卡片 80/天, Super Hi 0/天
 *   MONTHLY: 右划 15/天, 卡片 120/天, Super Hi 1/天
 *   YEARLY:  右划 15/天, 卡片 120/天, Super Hi 1/天
 *
 * 配额刷新 = UTC 00:00（key 带 yyyymmdd,自动按日轮转）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final StringRedisTemplate stringRedisTemplate;
    private final PaymentClient paymentClient;

    /** 配额 HASH 的 TTL 36 小时，覆盖整日 + 跨日容差 */
    private static final long QUOTA_TTL_HOURS = 36;

    /** 默认档位（payment-service 未接入时兜底） */
    static final String TIER_FREE = "FREE";
    static final String TIER_WEEKLY = "WEEKLY";
    static final String TIER_MONTHLY = "MONTHLY";
    static final String TIER_YEARLY = "YEARLY";

    /** 订阅配额表（每日上限） */
    private static final Map<String, QuotaLimit> TIER_LIMITS = Map.of(
            TIER_FREE,    new QuotaLimit(5, 50, 0),
            TIER_WEEKLY,  new QuotaLimit(10, 80, 0),
            TIER_MONTHLY, new QuotaLimit(15, 120, 1),
            TIER_YEARLY,  new QuotaLimit(15, 120, 1)
    );

    /** Super Hi 金币价格 */
    public static final int SUPER_HI_COIN_PRICE = 100;

    // ── 查询 ──

    /** 获取用户当日配额使用情况 */
    public QuotaInfo getQuota(Long userId) {
        String key = quotaKey(userId);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return new QuotaInfo(0, 0, 0);
        }
        return new QuotaInfo(
                intVal(entries.get("right_swipe")),
                intVal(entries.get("cards")),
                intVal(entries.get("super_hi"))
        );
    }

    /** 检查右划配额是否充足（不扣减） */
    public void checkRightSwipe(Long userId, String tier) {
        QuotaLimit limit = TIER_LIMITS.getOrDefault(tier, TIER_LIMITS.get(TIER_FREE));
        String key = quotaKey(userId);
        String val = (String) stringRedisTemplate.opsForHash().get(key, "right_swipe");
        int used = val != null ? Integer.parseInt(val) : 0;
        if (used >= limit.rightSwipe) {
            throw new BizException(ErrorCodes.QUOTA_RIGHT_SWIPE_EXCEEDED,
                    "今日右划已达上限 (" + limit.rightSwipe + ")");
        }
    }

    /** 检查卡片配额是否充足 */
    public void checkCard(Long userId, String tier) {
        QuotaLimit limit = TIER_LIMITS.getOrDefault(tier, TIER_LIMITS.get(TIER_FREE));
        String key = quotaKey(userId);
        String val = (String) stringRedisTemplate.opsForHash().get(key, "cards");
        int used = val != null ? Integer.parseInt(val) : 0;
        if (used >= limit.cards) {
            throw new BizException(ErrorCodes.QUOTA_CARD_EXCEEDED,
                    "今日可划卡片已达上限 (" + limit.cards + ")");
        }
    }

    /** 检查 Super Hi 配额是否充足（含金币购买兜底） */
    public boolean checkSuperHi(Long userId, String tier) {
        QuotaLimit limit = TIER_LIMITS.getOrDefault(tier, TIER_LIMITS.get(TIER_FREE));
        if (limit.superHi <= 0) {
            // 该档位不送 Super Hi，需要金币购买
            return false;
        }
        String key = quotaKey(userId);
        String val = (String) stringRedisTemplate.opsForHash().get(key, "super_hi");
        int used = val != null ? Integer.parseInt(val) : 0;
        return used < limit.superHi;
    }

    // ── 扣减 ──

    /** 扣减右划次数（失败回滚由调用方处理） */
    public void deductRightSwipe(Long userId) {
        String key = quotaKey(userId);
        stringRedisTemplate.opsForHash().increment(key, "right_swipe", 1);
        stringRedisTemplate.expire(key, java.time.Duration.ofHours(QUOTA_TTL_HOURS));
    }

    /** 扣减卡片数 */
    public void deductCard(Long userId) {
        String key = quotaKey(userId);
        stringRedisTemplate.opsForHash().increment(key, "cards", 1);
        stringRedisTemplate.expire(key, java.time.Duration.ofHours(QUOTA_TTL_HOURS));
    }

    /** 扣减 Super Hi 赠送次数 */
    public void deductSuperHi(Long userId) {
        String key = quotaKey(userId);
        stringRedisTemplate.opsForHash().increment(key, "super_hi", 1);
        stringRedisTemplate.expire(key, java.time.Duration.ofHours(QUOTA_TTL_HOURS));
    }

    /** 配额回滚（超额检测后调用） */
    public void rollbackRightSwipe(Long userId) {
        String key = quotaKey(userId);
        stringRedisTemplate.opsForHash().increment(key, "right_swipe", -1);
    }

    /** 配额回滚（卡片） */
    public void rollbackCard(Long userId) {
        String key = quotaKey(userId);
        stringRedisTemplate.opsForHash().increment(key, "cards", -1);
    }

    /** 获取用户订阅档位 */
    public String getUserTier(Long userId) {
        return paymentClient.getUserTier(userId);
    }

    /** 是否可以金币购买 Super Hi */
    public boolean canCoinBuySuperHi(Long userId) {
        return paymentClient.consumeCoins(userId, SUPER_HI_COIN_PRICE, "SUPER_HI",
                "super_hi_" + userId + "_" + java.time.LocalDate.now());
    }

    /** 获取当日超级 Hi 赠送限额 */
    public int getSuperHiFreeLimit(String tier) {
        return TIER_LIMITS.getOrDefault(tier, TIER_LIMITS.get(TIER_FREE)).superHi;
    }

    /** 获取指定档位的卡片日配额 */
    public int getCardLimit(String tier) {
        return TIER_LIMITS.getOrDefault(tier, TIER_LIMITS.get(TIER_FREE)).cards;
    }

    /** 获取指定档位的右划日配额 */
    public int getRightSwipeLimit(String tier) {
        return TIER_LIMITS.getOrDefault(tier, TIER_LIMITS.get(TIER_FREE)).rightSwipe;
    }

    // ── 内部方法 ──

    private String quotaKey(Long userId) {
        String yyyymmdd = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return CacheKeys.quota(userId, yyyymmdd);
    }

    private int intVal(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(val.toString());
    }

    // ── 值类型 ──

    public record QuotaLimit(int rightSwipe, int cards, int superHi) {}
    public record QuotaInfo(int rightSwipeUsed, int cardsUsed, int superHiUsed) {}
}
