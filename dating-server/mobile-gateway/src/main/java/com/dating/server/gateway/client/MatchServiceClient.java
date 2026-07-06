package com.dating.server.gateway.client;

import com.dating.proto.match.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * match-service 的 gRPC 客户端封装。
 * <p>
 * 覆盖划卡、喜欢列表、回应喜欢、匹配列表、访问记录等 7 个 RPC。
 * direction: 1=LEFT 2=RIGHT 3=SUPER_HI | targetUserType/fromUserType: 1=BH 2=DH
 */
@Component
public class MatchServiceClient {

    @GrpcClient("match-service")
    private MatchServiceGrpc.MatchServiceBlockingStub matchStub;

    /** 划卡：左滑/右滑/超级喜欢。右滑且对方也右滑过时 matched=true */
    public SwipeResponse swipe(long userId, long targetUserId, int direction, int targetUserType) {
        var req = SwipeRequest.newBuilder()
                .setUserId(userId)
                .setTargetUserId(targetUserId)
                .setDirection(direction)
                .setTargetUserType(targetUserType)
                .build();
        return matchStub.swipe(req);
    }

    /** 自己的划卡历史（游标分页） */
    public ListSwipesResponse listSwipes(long userId, int pageSize, long cursor) {
        var req = ListSwipesRequest.newBuilder()
                .setUserId(userId)
                .setPageSize(pageSize)
                .setCursor(cursor)
                .build();
        return matchStub.listSwipes(req);
    }

    /** 喜欢过我的人：右滑/超级喜欢过当前用户的列表 */
    public GetWhoLikedMeResponse getWhoLikedMe(long userId, int pageSize, long cursor) {
        var req = GetWhoLikedMeRequest.newBuilder()
                .setUserId(userId)
                .setPageSize(pageSize)
                .setCursor(cursor)
                .build();
        return matchStub.getWhoLikedMe(req);
    }

    /** 回应 like：likeBack=true 则双向匹配，false 则忽略 */
    public ReplyLikeResponse replyLike(long userId, long likeRecordId, boolean likeBack) {
        var req = ReplyLikeRequest.newBuilder()
                .setUserId(userId)
                .setLikeRecordId(likeRecordId)
                .setLikeBack(likeBack)
                .build();
        return matchStub.replyLike(req);
    }

    /** 已匹配列表（游标分页） */
    public ListMatchesResponse listMatches(long userId, int pageSize, long cursor) {
        var req = ListMatchesRequest.newBuilder()
                .setUserId(userId)
                .setPageSize(pageSize)
                .setCursor(cursor)
                .build();
        return matchStub.listMatches(req);
    }

    /** 记录访问行为：source=1 手动查看资料 / 2=DH 在线计划 / 3=DH 离线计划 */
    public RecordVisitResponse recordVisit(long fromUserId, long toUserId, int fromUserType, int source) {
        var req = RecordVisitRequest.newBuilder()
                .setFromUserId(fromUserId)
                .setToUserId(toUserId)
                .setFromUserType(fromUserType)
                .setSource(source)
                .build();
        return matchStub.recordVisit(req);
    }

    /** 谁看过我（游标分页，含 visit_count） */
    public ListVisitorsResponse listVisitors(long userId, int pageSize, long cursor) {
        var req = ListVisitorsRequest.newBuilder()
                .setUserId(userId)
                .setPageSize(pageSize)
                .setCursor(cursor)
                .build();
        return matchStub.listVisitors(req);
    }
}
