package com.dating.server.im.grpc;

import com.dating.proto.common.Result;
import com.dating.proto.im.*;
import com.dating.server.im.service.ImPresenceService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ImPresenceGrpcService extends ImPresenceGrpc.ImPresenceImplBase {

    private final ImPresenceService presenceService;

    @Override
    public void getPresence(GetPresenceRequest request, StreamObserver<GetPresenceResponse> responseObserver) {
        try {
            ImPresenceService.PresenceInfo info = presenceService.getPresence(request.getUserId());
            responseObserver.onNext(GetPresenceResponse.newBuilder()
                    .setPresence(toProtoPresence(info))
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetPresence error: userId={}", request.getUserId(), e);
            responseObserver.onNext(GetPresenceResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void batchGetPresence(BatchGetPresenceRequest request,
                                  StreamObserver<BatchGetPresenceResponse> responseObserver) {
        try {
            List<ImPresenceService.PresenceInfo> list = presenceService.batchGetPresence(request.getUserIdsList());
            BatchGetPresenceResponse.Builder builder = BatchGetPresenceResponse.newBuilder().setResult(success());
            for (ImPresenceService.PresenceInfo info : list) {
                builder.addPresences(toProtoPresence(info));
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("BatchGetPresence error", e);
            responseObserver.onNext(BatchGetPresenceResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
        try {
            presenceService.heartbeat(request.getUserId(), request.getStatus());
            responseObserver.onNext(HeartbeatResponse.newBuilder()
                    .setSuccess(true)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Heartbeat error: userId={}", request.getUserId(), e);
            responseObserver.onNext(HeartbeatResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listOnlineUserIds(ListOnlineUserIdsRequest request,
                                   StreamObserver<ListOnlineUserIdsResponse> responseObserver) {
        try {
            List<Long> userIds = presenceService.listOnlineUserIds(
                    request.getSinceMs(), request.getUntilMs(), request.getLimit());
            responseObserver.onNext(ListOnlineUserIdsResponse.newBuilder()
                    .addAllUserIds(userIds)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ListOnlineUserIds error", e);
            responseObserver.onNext(ListOnlineUserIdsResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listRecentOfflineUsers(ListRecentOfflineUsersRequest request,
                                        StreamObserver<ListRecentOfflineUsersResponse> responseObserver) {
        try {
            List<Long> userIds = presenceService.listRecentOfflineUsers(
                    request.getSinceMs(), request.getUntilMs(), request.getLimit());
            responseObserver.onNext(ListRecentOfflineUsersResponse.newBuilder()
                    .addAllUserIds(userIds)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ListRecentOfflineUsers error", e);
            responseObserver.onNext(ListRecentOfflineUsersResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void ensureConversation(EnsureConversationRequest request,
                                    StreamObserver<EnsureConversationResponse> responseObserver) {
        try {
            String conversationId = presenceService.ensureConversation(
                    request.getUserIdA(), request.getUserIdB());
            responseObserver.onNext(EnsureConversationResponse.newBuilder()
                    .setConversationId(conversationId != null ? conversationId : "")
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("EnsureConversation error: userIdA={}, userIdB={}",
                    request.getUserIdA(), request.getUserIdB(), e);
            responseObserver.onNext(EnsureConversationResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void sendSystemMessage(SendSystemMessageRequest request,
                                   StreamObserver<SendSystemMessageResponse> responseObserver) {
        try {
            boolean success = presenceService.sendSystemMessage(
                    request.getToUserId(), request.getTitle(), request.getBody());
            responseObserver.onNext(SendSystemMessageResponse.newBuilder()
                    .setSuccess(success)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("SendSystemMessage error: toUserId={}", request.getToUserId(), e);
            responseObserver.onNext(SendSystemMessageResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void triggerDhOpening(TriggerDhOpeningRequest request,
                                  StreamObserver<TriggerDhOpeningResponse> responseObserver) {
        try {
            boolean success = presenceService.triggerDhOpening(
                    request.getDhUserId(), request.getTargetUserId());
            responseObserver.onNext(TriggerDhOpeningResponse.newBuilder()
                    .setSuccess(success)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("TriggerDhOpening error: dhUserId={}, targetUserId={}",
                    request.getDhUserId(), request.getTargetUserId(), e);
            responseObserver.onNext(TriggerDhOpeningResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    private Presence toProtoPresence(ImPresenceService.PresenceInfo info) {
        return Presence.newBuilder()
                .setUserId(info.userId())
                .setOnline(info.online())
                .setLastHeartbeatAt(info.lastHeartbeatAt())
                .setStatus(info.status())
                .build();
    }

    private Result success() { return Result.newBuilder().setCode(0).setMessage("ok").build(); }
    private Result error(int code, String message) { return Result.newBuilder().setCode(code).setMessage(message).build(); }
}
