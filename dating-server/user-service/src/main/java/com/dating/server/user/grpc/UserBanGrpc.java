package com.dating.server.user.grpc;

import com.dating.proto.common.Result;
import com.dating.proto.user.*;
import com.dating.server.user.dto.BanResult;
import com.dating.server.user.service.UserBanService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserBanGrpc extends UserBanServiceGrpc.UserBanServiceImplBase {

    private final UserBanService userBanService;

    @Override
    public void checkBan(CheckBanRequest request, StreamObserver<CheckBanResponse> responseObserver) {
        try {
            BanResult banResult = userBanService.checkBan(request.getUserId());

            CheckBanResponse.Builder builder = CheckBanResponse.newBuilder()
                    .setBanned(banResult.isBanned())
                    .setResult(success());

            if (banResult.getReason() != null) {
                builder.setReason(banResult.getReason());
            }
            if (banResult.getBannedAtMs() != null) {
                builder.setBannedAtMs(banResult.getBannedAtMs());
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("CheckBan error: userId={}", request.getUserId(), e);
            responseObserver.onNext(CheckBanResponse.newBuilder()
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
