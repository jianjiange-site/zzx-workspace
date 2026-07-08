package com.dating.server.match.grpc;

import com.dating.proto.common.Result;
import com.dating.proto.match.*;
import com.dating.server.match.entity.LikeRecord;
import com.dating.server.match.entity.Match;
import com.dating.server.match.entity.SwipeHistory;
import com.dating.server.match.entity.VisitRecord;
import com.dating.server.match.exception.BizException;
import com.dating.server.match.service.FeedService;
import com.dating.server.match.service.MatchService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class MatchGrpcService extends MatchServiceGrpc.MatchServiceImplBase {

    private final MatchService matchService;
    private final FeedService feedService;

    @Override
    public void getTodayFeed(GetTodayFeedRequest request,
                              StreamObserver<GetTodayFeedResponse> responseObserver) {
        try {
            int count = request.getCount() > 0 ? Math.min(request.getCount(), 20) : 5;
            FeedService.FeedResult result = feedService.getTodayFeed(request.getUserId(), count);

            GetTodayFeedResponse.Builder builder = GetTodayFeedResponse.newBuilder()
                    .setResult(success())
                    .setExhausted(result.exhausted());

            for (FeedService.CardVO card : result.cards()) {
                builder.addCards(Card.newBuilder()
                        .setTargetUserId(card.targetUserId())
                        .setTargetUserType(card.targetUserType())
                        .setNickname(card.nickname() != null ? card.nickname() : "")
                        .setAge(card.age() != null ? card.age() : 0)
                        .addAllPhotoKeys(card.photoKeys() != null ? card.photoKeys() : List.of())
                        .setBio(card.bio() != null ? card.bio() : "")
                        .setDistanceKm(card.distanceKm() != null ? card.distanceKm() : -1));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetTodayFeed error: userId={}", request.getUserId(), e);
            responseObserver.onNext(GetTodayFeedResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void swipe(SwipeRequest request, StreamObserver<SwipeResponse> responseObserver) {
        try {
            MatchService.SwipeResult result = matchService.swipe(
                    request.getUserId(),
                    request.getTargetUserId(),
                    request.getDirection(),
                    request.getTargetUserType());

            responseObserver.onNext(SwipeResponse.newBuilder()
                    .setMatched(result.matched())
                    .setMatchId(result.matchId() != null ? result.matchId() : 0)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (BizException e) {
            responseObserver.onNext(SwipeResponse.newBuilder()
                    .setResult(error(e.getCode(), e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Swipe error: userId={}, targetUserId={}",
                    request.getUserId(), request.getTargetUserId(), e);
            responseObserver.onNext(SwipeResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listSwipes(ListSwipesRequest request, StreamObserver<ListSwipesResponse> responseObserver) {
        try {
            int pageSize = request.getPageSize() > 0 ? Math.min(request.getPageSize(), 50) : 20;
            Long cursor = request.getCursor() > 0 ? request.getCursor() : null;

            List<SwipeHistory> list = matchService.listSwipes(
                    request.getUserId(), pageSize, cursor);

            ListSwipesResponse.Builder builder = ListSwipesResponse.newBuilder()
                    .setResult(success());
            boolean hasMore = list.size() > pageSize;
            int limit = hasMore ? pageSize : list.size();
            for (int i = 0; i < limit; i++) {
                SwipeHistory s = list.get(i);
                builder.addItems(SwipeRecord.newBuilder()
                        .setId(s.getId())
                        .setTargetUserId(s.getTargetUserId())
                        .setDirection(s.getDirection())
                        .setTargetUserType(s.getTargetUserType())
                        .setSwipedAt(s.getSwipedAt().toEpochMilli())
                        .build());
            }
            if (hasMore) {
                builder.setNextCursor(list.get(pageSize - 1).getId());
                builder.setHasMore(true);
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ListSwipes error: userId={}", request.getUserId(), e);
            responseObserver.onNext(ListSwipesResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getWhoLikedMe(GetWhoLikedMeRequest request,
                               StreamObserver<GetWhoLikedMeResponse> responseObserver) {
        try {
            int pageSize = request.getPageSize() > 0 ? Math.min(request.getPageSize(), 50) : 20;
            Long cursor = request.getCursor() > 0 ? request.getCursor() : null;

            List<LikeRecord> list = matchService.whoLikedMe(
                    request.getUserId(), pageSize, cursor);
            int totalCount = list.isEmpty() ? 0
                    : matchService.countUnhandledLikes(request.getUserId(),
                            list.get(0).getId());

            GetWhoLikedMeResponse.Builder builder = GetWhoLikedMeResponse.newBuilder()
                    .setResult(success())
                    .setTotalCount(totalCount);
            boolean hasMore = list.size() > pageSize;
            int limit = hasMore ? pageSize : list.size();
            for (int i = 0; i < limit; i++) {
                LikeRecord r = list.get(i);
                builder.addItems(WhoLikedMeItem.newBuilder()
                        .setId(r.getId())
                        .setFromUserId(r.getFromUserId())
                        .setFromUserType(r.getFromUserType())
                        .setLikedAt(r.getLikedAt().toEpochMilli())
                        .build());
            }
            if (hasMore) {
                builder.setNextCursor(list.get(pageSize - 1).getId());
                builder.setHasMore(true);
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetWhoLikedMe error: userId={}", request.getUserId(), e);
            responseObserver.onNext(GetWhoLikedMeResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void replyLike(ReplyLikeRequest request, StreamObserver<ReplyLikeResponse> responseObserver) {
        try {
            MatchService.ReplyLikeResult result = matchService.replyLike(
                    request.getUserId(), request.getLikeRecordId(), request.getLikeBack());

            responseObserver.onNext(ReplyLikeResponse.newBuilder()
                    .setMatched(result.matched())
                    .setMatchId(result.matchId() != null ? result.matchId() : 0)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (BizException e) {
            responseObserver.onNext(ReplyLikeResponse.newBuilder()
                    .setResult(error(e.getCode(), e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ReplyLike error: userId={}, likeRecordId={}",
                    request.getUserId(), request.getLikeRecordId(), e);
            responseObserver.onNext(ReplyLikeResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listMatches(ListMatchesRequest request,
                            StreamObserver<ListMatchesResponse> responseObserver) {
        try {
            int pageSize = request.getPageSize() > 0 ? Math.min(request.getPageSize(), 50) : 20;
            Long cursor = request.getCursor() > 0 ? request.getCursor() : null;

            List<Match> list = matchService.listMatches(
                    request.getUserId(), pageSize, cursor);

            ListMatchesResponse.Builder builder = ListMatchesResponse.newBuilder()
                    .setResult(success());
            boolean hasMore = list.size() > pageSize;
            int limit = hasMore ? pageSize : list.size();
            for (int i = 0; i < limit; i++) {
                Match m = list.get(i);
                // 对方 user_id = 两个 ID 中不等于当前用户的那个
                long peerUserId = m.getUserIdLow().equals(request.getUserId())
                        ? m.getUserIdHigh() : m.getUserIdLow();
                builder.addItems(MatchItem.newBuilder()
                        .setMatchId(m.getId())
                        .setUserId(peerUserId)
                        .setMatchedAt(m.getMatchedAt().toEpochMilli())
                        .setSource(m.getSource())
                        .build());
            }
            if (hasMore) {
                builder.setNextCursor(list.get(pageSize - 1).getId());
                builder.setHasMore(true);
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ListMatches error: userId={}", request.getUserId(), e);
            responseObserver.onNext(ListMatchesResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void recordVisit(RecordVisitRequest request,
                            StreamObserver<RecordVisitResponse> responseObserver) {
        try {
            matchService.recordVisit(
                    request.getFromUserId(),
                    request.getToUserId(),
                    request.getFromUserType(),
                    request.getSource());

            responseObserver.onNext(RecordVisitResponse.newBuilder()
                    .setSuccess(true)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("RecordVisit error: fromUserId={}, toUserId={}",
                    request.getFromUserId(), request.getToUserId(), e);
            responseObserver.onNext(RecordVisitResponse.newBuilder()
                    .setSuccess(false)
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listVisitors(ListVisitorsRequest request,
                             StreamObserver<ListVisitorsResponse> responseObserver) {
        try {
            int pageSize = request.getPageSize() > 0 ? Math.min(request.getPageSize(), 50) : 20;
            Long cursor = request.getCursor() > 0 ? request.getCursor() : null;

            List<VisitRecord> list = matchService.listVisitors(
                    request.getUserId(), pageSize, cursor);

            ListVisitorsResponse.Builder builder = ListVisitorsResponse.newBuilder()
                    .setResult(success());
            boolean hasMore = list.size() > pageSize;
            int limit = hasMore ? pageSize : list.size();
            for (int i = 0; i < limit; i++) {
                VisitRecord v = list.get(i);
                builder.addItems(VisitorItem.newBuilder()
                        .setId(v.getId())
                        .setFromUserId(v.getFromUserId())
                        .setFromUserType(v.getFromUserType())
                        .setVisitCount(v.getVisitCount())
                        .setVisitedAt(v.getVisitedAt().toEpochMilli())
                        .build());
            }
            if (hasMore) {
                builder.setNextCursor(list.get(pageSize - 1).getId());
                builder.setHasMore(true);
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ListVisitors error: userId={}", request.getUserId(), e);
            responseObserver.onNext(ListVisitorsResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ── 辅助方法 ──

    private Result success() {
        return Result.newBuilder().setCode(0).setMessage("ok").build();
    }

    private Result error(int code, String message) {
        return Result.newBuilder().setCode(code).setMessage(message).build();
    }
}
