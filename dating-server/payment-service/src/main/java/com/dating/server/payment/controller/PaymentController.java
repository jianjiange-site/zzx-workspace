package com.dating.server.payment.controller;

import com.dating.server.payment.service.CoinService;
import com.dating.server.payment.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final CoinService coinService;
    private final SubscriptionService subscriptionService;

    // ── 金币 ──

    /** 查询金币余额 */
    @GetMapping("/users/{userId}/coins")
    public GetCoinsResponse getCoins(@PathVariable Long userId) {
        long coins = coinService.getCoins(userId);
        return new GetCoinsResponse(coins);
    }

    /** 扣金币 */
    @PostMapping("/coins/consume")
    public ConsumeCoinsResponse consumeCoins(@RequestBody ConsumeCoinsRequest req) {
        boolean ok = coinService.consumeCoins(req.userId(), req.amount(), req.reason(), req.idempotencyKey());
        return new ConsumeCoinsResponse(ok);
    }

    // ── 订阅 ──

    /** 查询订阅 */
    @GetMapping("/users/{userId}/subscription")
    public SubscriptionInfoResponse getSubscription(@PathVariable Long userId) {
        SubscriptionService.SubscriptionInfo info = subscriptionService.getSubscription(userId);
        return new SubscriptionInfoResponse(info.tier(), info.active(),
                info.expiresAt() != null ? info.expiresAt().toEpochMilli() : null);
    }

    /** 激活订阅 */
    @PostMapping("/subscriptions/activate")
    public ActivateSubscriptionResponse activateSubscription(@RequestBody ActivateSubscriptionRequest req) {
        subscriptionService.activateSubscription(req.userId(), req.tier(), req.durationDays(), req.source());
        return new ActivateSubscriptionResponse(true);
    }

    // ── DTO ──

    record GetCoinsResponse(long coins) {}
    record ConsumeCoinsRequest(long userId, long amount, String reason, String idempotencyKey) {}
    record ConsumeCoinsResponse(boolean success) {}
    record SubscriptionInfoResponse(String tier, boolean active, Long expiresAt) {}
    record ActivateSubscriptionRequest(long userId, int tier, int durationDays, String source) {}
    record ActivateSubscriptionResponse(boolean success) {}
}
