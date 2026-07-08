package com.dating.server.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.payment.constant.Constants;
import com.dating.server.payment.entity.UserSubscription;
import com.dating.server.payment.mapper.UserSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 订阅服务 —— 档位判定与激活
 *
 * 设计文档 §7：
 * - 无记录 / 过期 → FREE
 * - 有效订阅 → 返回对应 tier + expires_at
 * - 激活时只升不降 + 时长顺延
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final UserSubscriptionMapper subscriptionMapper;

    /** 订阅信息 */
    public record SubscriptionInfo(String tier, boolean active, Instant expiresAt) {}

    /**
     * 获取用户当前订阅
     *
     * tier 映射：1=FREE 2=WEEKLY 3=MONTHLY 4=YEARLY
     * 无记录或已过期 → tier=FREE, active=false
     */
    public SubscriptionInfo getSubscription(Long userId) {
        UserSubscription sub = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId)
                        .eq(UserSubscription::getDeleted, false));
        if (sub == null) {
            return new SubscriptionInfo("FREE", false, null);
        }

        boolean expired = sub.getExpiresAt() != null
                && sub.getExpiresAt().isBefore(Instant.now());
        if (expired || sub.getTier() <= Constants.TIER_FREE) {
            return new SubscriptionInfo("FREE", false, sub.getExpiresAt());
        }

        String tierName = switch (sub.getTier()) {
            case Constants.TIER_WEEKLY -> "WEEKLY";
            case Constants.TIER_MONTHLY -> "MONTHLY";
            case Constants.TIER_YEARLY -> "YEARLY";
            default -> "FREE";
        };
        return new SubscriptionInfo(tierName, true, sub.getExpiresAt());
    }

    /**
     * 激活 / 续期订阅
     *
     * 规则：
     * - newTier <= FREE → 忽略
     * - 无记录 → INSERT
     * - 未过期 → 只升不降，expires_at 顺延
     * - 已过期 → 从 now 重新算
     */
    @Transactional
    public void activateSubscription(Long userId, int newTier, int durationDays, String source) {
        if (newTier <= Constants.TIER_FREE) return;

        UserSubscription sub = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId)
                        .eq(UserSubscription::getDeleted, false));

        Instant now = Instant.now();
        if (sub == null) {
            // 无记录 → INSERT
            sub = new UserSubscription();
            sub.setUserId(userId);
            sub.setTier(newTier);
            sub.setExpiresAt(now.plusSeconds(durationDays * 86400L));
            sub.setSource(source);
            subscriptionMapper.insert(sub);
            log.info("新订阅激活: userId={}, tier={}, expires={}", userId, newTier, sub.getExpiresAt());
        } else {
            boolean expired = sub.getExpiresAt() != null
                    && sub.getExpiresAt().isBefore(now);
            if (expired) {
                // 已过期 → 从 now 重新算
                sub.setTier(newTier);
                sub.setExpiresAt(now.plusSeconds(durationDays * 86400L));
            } else {
                // 未过期 → 只升不降 + 时长顺延
                if (newTier > sub.getTier()) {
                    sub.setTier(newTier);
                }
                sub.setExpiresAt(sub.getExpiresAt().plusSeconds(durationDays * 86400L));
            }
            sub.setSource(source);
            subscriptionMapper.updateById(sub);
            log.info("订阅续期/升级: userId={}, tier={}, expires={}", userId, sub.getTier(), sub.getExpiresAt());
        }
    }
}
