package com.dating.server.match.client;

import com.dating.proto.payment.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * payment-service gRPC 客户端
 *
 * 封装订阅档位查询和金币扣减，带 fallback（远端异常时降级为 FREE 档位）。
 */
@Slf4j
@Component
public class PaymentClient {

    @GrpcClient("payment-service")
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentStub;

    /**
     * 查询用户订阅档位
     *
     * @return tierName (FREE / WEEKLY / MONTHLY / YEARLY)
     */
    public String getUserTier(Long userId) {
        try {
            GetSubscriptionResponse resp = paymentStub.getSubscription(
                    GetSubscriptionRequest.newBuilder().setUserId(userId).build());
            if (resp.getActive()) {
                return switch (resp.getTier()) {
                    case TIER_WEEKLY -> "WEEKLY";
                    case TIER_MONTHLY -> "MONTHLY";
                    case TIER_YEARLY -> "YEARLY";
                    default -> "FREE";
                };
            }
            return "FREE";
        } catch (Exception e) {
            log.error("payment-service gRPC call failed for getSubscription, userId={}, fallback to FREE",
                    userId, e);
            return "FREE";
        }
    }

    /**
     * 扣金币（幂等）
     *
     * @return true 扣减成功 / false 余额不足
     */
    public boolean consumeCoins(Long userId, long amount, String reason, String idempotencyKey) {
        try {
            ConsumeCoinsResponse resp = paymentStub.consumeCoins(
                    ConsumeCoinsRequest.newBuilder()
                            .setUserId(userId)
                            .setAmount(amount)
                            .setReason(reason)
                            .setIdempotencyKey(idempotencyKey)
                            .build());
            return resp.getSuccess();
        } catch (Exception e) {
            log.error("payment-service gRPC call failed for consumeCoins, userId={}, amount={}",
                    userId, amount, e);
            return false;
        }
    }
}
