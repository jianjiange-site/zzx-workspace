package com.dating.server.user.grpc;

import com.dating.proto.common.Result;
import com.dating.proto.user.*;
import com.dating.server.user.dto.ReplaceInterestItem;
import com.dating.server.user.entity.UserInterest;
import com.dating.server.user.manager.UserInterestManager;
import com.dating.server.user.service.UserInterestService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserInterestGrpc extends UserInterestServiceGrpc.UserInterestServiceImplBase {

    private final UserInterestService userInterestService;
    private final UserInterestManager userInterestManager;

    @Override
    public void getInterests(GetInterestsRequest request,
                             StreamObserver<GetInterestsResponse> responseObserver) {
        try {
            List<UserInterest> interests = userInterestManager.getCachedByUserId(request.getUserId());

            GetInterestsResponse.Builder builder = GetInterestsResponse.newBuilder()
                    .setResult(success());
            for (UserInterest interest : interests) {
                builder.addItems(InterestItem.newBuilder()
                        .setId(interest.getId())
                        .setType(interest.getType())
                        .setPicKey(interest.getPicKey() != null ? interest.getPicKey() : "")
                        .setContent(interest.getContent() != null ? interest.getContent() : "")
                        .setSortOrder(interest.getSortOrder() != null ? interest.getSortOrder() : 0)
                        .build());
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetInterests error: userId={}", request.getUserId(), e);
            responseObserver.onNext(GetInterestsResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void replaceInterests(ReplaceInterestsRequest request,
                                 StreamObserver<ReplaceInterestsResponse> responseObserver) {
        try {
            List<ReplaceInterestItem> items = request.getItemsList().stream()
                    .map(item -> {
                        ReplaceInterestItem dto = new ReplaceInterestItem();
                        dto.setType(item.getType());
                        dto.setPicKey(item.getPicKey());
                        dto.setContent(item.getContent());
                        dto.setSortOrder(item.getSortOrder());
                        return dto;
                    })
                    .collect(Collectors.toList());

            userInterestService.replaceUserInterests(request.getUserId(), items);

            responseObserver.onNext(ReplaceInterestsResponse.newBuilder()
                    .setSuccess(true)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ReplaceInterests error: userId={}", request.getUserId(), e);
            responseObserver.onNext(ReplaceInterestsResponse.newBuilder()
                    .setSuccess(false)
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
