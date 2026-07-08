package com.dating.server.match.client;

import com.dating.proto.im.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * im-service gRPC 客户端
 *
 * 封装在线状态查询、会话创建、系统消息和 DH 开场白触发。
 * 所有方法带 fallback，远端异常时返回降级结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImServiceClient {

    @GrpcClient("im-service")
    private ImPresenceGrpc.ImPresenceBlockingStub imPresenceStub;

    /**
     * 确保匹配双方已创建 IM 会话（幂等）
     *
     * @return conversationId（成功时）, null（失败时）
     */
    public String ensureConversation(Long userIdA, Long userIdB) {
        try {
            EnsureConversationResponse resp = imPresenceStub.ensureConversation(
                    EnsureConversationRequest.newBuilder()
                            .setUserIdA(userIdA)
                            .setUserIdB(userIdB)
                            .build());
            if (resp.getResult().getCode() == 0) {
                return resp.getConversationId();
            }
            log.warn("ensureConversation 返回失败: code={}, msg={}",
                    resp.getResult().getCode(), resp.getResult().getMessage());
            return null;
        } catch (Exception e) {
            log.error("im-service ensureConversation 失败: userIdA={}, userIdB={}", userIdA, userIdB, e);
            return null;
        }
    }

    /**
     * 发送系统消息
     */
    public boolean sendSystemMessage(Long toUserId, String title, String body) {
        try {
            SendSystemMessageResponse resp = imPresenceStub.sendSystemMessage(
                    SendSystemMessageRequest.newBuilder()
                            .setToUserId(toUserId)
                            .setTitle(title != null ? title : "")
                            .setBody(body != null ? body : "")
                            .build());
            return resp.getResult().getCode() == 0;
        } catch (Exception e) {
            log.error("im-service sendSystemMessage 失败: toUserId={}", toUserId, e);
            return false;
        }
    }

    /**
     * 触发 DH 开场白（匹配后 ai-chat 生成第一条消息）
     */
    public boolean triggerDhOpening(Long dhUserId, Long targetUserId) {
        try {
            TriggerDhOpeningResponse resp = imPresenceStub.triggerDhOpening(
                    TriggerDhOpeningRequest.newBuilder()
                            .setDhUserId(dhUserId)
                            .setTargetUserId(targetUserId)
                            .build());
            return resp.getResult().getCode() == 0;
        } catch (Exception e) {
            log.error("im-service triggerDhOpening 失败: dhUserId={}, targetUserId={}",
                    dhUserId, targetUserId, e);
            return false;
        }
    }

    /**
     * 获取在线用户 ID 列表（供 OnlinePlanGenerator 使用）
     */
    public List<Long> listOnlineUserIds(Long sinceMs, Long untilMs, int limit) {
        try {
            ListOnlineUserIdsResponse resp = imPresenceStub.listOnlineUserIds(
                    ListOnlineUserIdsRequest.newBuilder()
                            .setSinceMs(sinceMs)
                            .setUntilMs(untilMs)
                            .setLimit(limit)
                            .build());
            return resp.getUserIdsList();
        } catch (Exception e) {
            log.error("im-service listOnlineUserIds 失败: sinceMs={}, untilMs={}", sinceMs, untilMs, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取最近离线用户 ID 列表（供 OfflinePlanGenerator 使用）
     */
    public List<Long> listRecentOfflineUsers(Long sinceMs, Long untilMs, int limit) {
        try {
            ListRecentOfflineUsersResponse resp = imPresenceStub.listRecentOfflineUsers(
                    ListRecentOfflineUsersRequest.newBuilder()
                            .setSinceMs(sinceMs)
                            .setUntilMs(untilMs)
                            .setLimit(limit)
                            .build());
            return resp.getUserIdsList();
        } catch (Exception e) {
            log.error("im-service listRecentOfflineUsers 失败: sinceMs={}, untilMs={}", sinceMs, untilMs, e);
            return Collections.emptyList();
        }
    }
}
