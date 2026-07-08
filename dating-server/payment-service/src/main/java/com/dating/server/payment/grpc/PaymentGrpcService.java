package com.dating.server.payment.grpc;

import com.dating.proto.common.Result;
import com.dating.proto.payment.*;
import com.dating.server.payment.service.CoinService;
import com.dating.server.payment.service.SubscriptionService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final CoinService coinService;
    private final SubscriptionService subscriptionService;

    @Override
    public void getCoins(GetCoinsRequest request, StreamObserver<GetCoinsResponse> responseObserver) {
        try {
            long coins = coinService.getCoins(request.getUserId());
            // 简化：不暴露 free/paid 明细，仅返回总额
            responseObserver.onNext(GetCoinsResponse.newBuilder()
                    .setCoins(coins)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetCoins error: userId={}", request.getUserId(), e);
            responseObserver.onNext(GetCoinsResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void consumeCoins(ConsumeCoinsRequest request, StreamObserver<ConsumeCoinsResponse> responseObserver) {
        try {
            boolean ok = coinService.consumeCoins(
                    request.getUserId(), request.getAmount(),
                    request.getReason(), request.getIdempotencyKey());

            responseObserver.onNext(ConsumeCoinsResponse.newBuilder()
                    .setSuccess(ok)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ConsumeCoins error: userId={}, amount={}",
                    request.getUserId(), request.getAmount(), e);
            responseObserver.onNext(ConsumeCoinsResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getSubscription(GetSubscriptionRequest request,
                                 StreamObserver<GetSubscriptionResponse> responseObserver) {
        try {
            SubscriptionService.SubscriptionInfo info = subscriptionService.getSubscription(request.getUserId());
            SubscriptionTier tier = switch (info.tier()) {
                case "FREE" -> SubscriptionTier.TIER_FREE;
                case "WEEKLY" -> SubscriptionTier.TIER_WEEKLY;
                case "MONTHLY" -> SubscriptionTier.TIER_MONTHLY;
                case "YEARLY" -> SubscriptionTier.TIER_YEARLY;
                default -> SubscriptionTier.TIER_FREE;
            };

            responseObserver.onNext(GetSubscriptionResponse.newBuilder()
                    .setTier(tier)
                    .setActive(info.active())
                    .setExpiresAt(info.expiresAt() != null ? info.expiresAt().toEpochMilli() : 0)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetSubscription error: userId={}", request.getUserId(), e);
            responseObserver.onNext(GetSubscriptionResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void activateSubscription(ActivateSubscriptionRequest request,
                                      StreamObserver<ActivateSubscriptionResponse> responseObserver) {
        try {
            subscriptionService.activateSubscription(
                    request.getUserId(), request.getTier().getNumber(),
                    request.getDurationDays(), request.getSource());

            responseObserver.onNext(ActivateSubscriptionResponse.newBuilder()
                    .setSuccess(true)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ActivateSubscription error: userId={}", request.getUserId(), e);
            responseObserver.onNext(ActivateSubscriptionResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    private Result success() {
        return Result.newBuilder().setCode(0).setMessage("ok").build();
    }

    private Result error(int code, String message) {
        return Result.newBuilder().setCode(code).setMessage(message).build();
    }
}
