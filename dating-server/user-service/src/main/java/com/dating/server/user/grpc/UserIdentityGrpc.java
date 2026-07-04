package com.dating.server.user.grpc;

import com.dating.proto.common.Result;
import com.dating.proto.user.*;
import com.dating.server.user.constant.AppName;
import com.dating.server.user.dto.IdentityResolveResult;
import com.dating.server.user.service.UserIdentityService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserIdentityGrpc extends UserIdentityServiceGrpc.UserIdentityServiceImplBase {

    private final UserIdentityService userIdentityService;

    @Override
    public void registerOrLoginByPhone(PhoneLoginRequest request,
                                       StreamObserver<PhoneLoginResponse> responseObserver) {
        try {
            IdentityResolveResult result = userIdentityService.resolveOrCreateByPhone(
                    request.getPhone(), AppName.ZZX_DATING.getValue());

            responseObserver.onNext(PhoneLoginResponse.newBuilder()
                    .setUserId(result.getUserId())
                    .setPending(result.isPending())
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("RegisterOrLoginByPhone error", e);
            responseObserver.onNext(PhoneLoginResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void registerOrLoginByThirdParty(ThirdPartyLoginRequest request,
                                            StreamObserver<ThirdPartyLoginResponse> responseObserver) {
        try {
            IdentityResolveResult result = userIdentityService.resolveOrCreateByThirdParty(
                    request.getPlatform(), request.getThirdPartyUserId(),
                    AppName.ZZX_DATING.getValue(), null);

            responseObserver.onNext(ThirdPartyLoginResponse.newBuilder()
                    .setUserId(result.getUserId())
                    .setPending(result.isPending())
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("RegisterOrLoginByThirdParty error", e);
            responseObserver.onNext(ThirdPartyLoginResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void registerOrLoginByDevice(DeviceLoginRequest request,
                                        StreamObserver<DeviceLoginResponse> responseObserver) {
        try {
            IdentityResolveResult result = userIdentityService.resolveOrCreateByDevice(
                    request.getDeviceId(), request.getPlatform(), AppName.ZZX_DATING.getValue());

            responseObserver.onNext(DeviceLoginResponse.newBuilder()
                    .setUserId(result.getUserId())
                    .setPending(result.isPending())
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("RegisterOrLoginByDevice error", e);
            responseObserver.onNext(DeviceLoginResponse.newBuilder()
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
