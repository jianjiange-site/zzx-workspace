package com.dating.server.match.controller;

import com.dating.server.match.service.FeedService;
import com.dating.server.match.service.MatchService;
import com.dating.server.match.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final FeedService feedService;
    private final QuotaService quotaService;

    // ── Feed ──

    /** 获取今日 feed 卡片 */
    @GetMapping("/users/{userId}/feed")
    public GetFeedResponse getFeed(@PathVariable Long userId,
                                   @RequestParam(defaultValue = "5") int count) {
        FeedService.FeedResult result = feedService.getTodayFeed(userId, count);
        List<CardItem> cards = result.cards().stream()
                .map(c -> new CardItem(c.targetUserId(), c.targetUserType()))
                .toList();
        return new GetFeedResponse(cards, result.exhausted());
    }

    // ── Swipe ──

    /** 划卡（左滑/右滑） */
    @PostMapping("/swipe")
    public SwipeResponse swipe(@RequestBody SwipeRequest req) {
        var result = matchService.swipe(req.userId, req.targetUserId, req.direction, req.targetUserType);
        return new SwipeResponse(result.matched(), result.matchId());
    }

    /** Super Hi（硬匹配） */
    @PostMapping("/super-hi")
    public SwipeResponse superHi(@RequestBody SuperHiRequest req) {
        var result = matchService.swipe(req.userId, req.targetUserId, 3, req.targetUserType);
        return new SwipeResponse(result.matched(), result.matchId());
    }

    // ── 配额 ──

    /** 查询配额 */
    @GetMapping("/users/{userId}/quota")
    public QuotaResponse getQuota(@PathVariable Long userId) {
        QuotaService.QuotaInfo info = quotaService.getQuota(userId);
        String tier = quotaService.getUserTier(userId);
        int superHiLimit = quotaService.getSuperHiFreeLimit(tier);
        return new QuotaResponse(
                info.rightSwipeUsed(), info.cardsUsed(), info.superHiUsed(),
                superHiLimit, QuotaService.SUPER_HI_COIN_PRICE, tier);
    }

    // ── 划卡历史 ──

    /** 划卡历史 */
    @GetMapping("/users/{userId}/swipes")
    public ListResponse swipeHistory(@PathVariable Long userId,
                                     @RequestParam(defaultValue = "20") int pageSize,
                                     @RequestParam(required = false) Long cursor) {
        var list = matchService.listSwipes(userId, pageSize, cursor);
        boolean hasMore = list.size() > pageSize;
        int limit = hasMore ? pageSize : list.size();
        var items = list.subList(0, limit).stream()
                .map(s -> new SwipeItem(s.getId(), s.getTargetUserId(), s.getDirection(),
                        s.getTargetUserType(), s.getSwipedAt().toEpochMilli()))
                .toList();
        return new ListResponse(items, hasMore ? list.get(pageSize - 1).getId() : null, hasMore);
    }

    // ── Like ──

    /** 喜欢过我的人 */
    @GetMapping("/users/{userId}/likes")
    public WhoLikedMeResponse whoLikedMe(@PathVariable Long userId,
                                          @RequestParam(defaultValue = "20") int pageSize,
                                          @RequestParam(required = false) Long cursor) {
        var list = matchService.whoLikedMe(userId, pageSize, cursor);
        int totalCount = list.isEmpty() ? 0 : matchService.countUnhandledLikes(userId, list.get(0).getId());
        boolean hasMore = list.size() > pageSize;
        int limit = hasMore ? pageSize : list.size();
        var items = list.subList(0, limit).stream()
                .map(r -> new LikedByItem(r.getId(), r.getFromUserId(), r.getFromUserType(), r.getLikedAt().toEpochMilli()))
                .toList();
        return new WhoLikedMeResponse(items, hasMore ? list.get(pageSize - 1).getId() : null, hasMore, totalCount);
    }

    /** 回应 like */
    @PostMapping("/likes/{likeRecordId}/reply")
    public ReplyLikeResponse replyLike(@PathVariable Long likeRecordId,
                                        @RequestBody ReplyLikeRequest req) {
        var result = matchService.replyLike(req.userId, likeRecordId, req.likeBack);
        return new ReplyLikeResponse(result.matched(), result.matchId());
    }

    // ── Match ──

    /** 匹配列表 */
    @GetMapping("/users/{userId}/matches")
    public ListResponse matches(@PathVariable Long userId,
                                @RequestParam(defaultValue = "20") int pageSize,
                                @RequestParam(required = false) Long cursor) {
        var list = matchService.listMatches(userId, pageSize, cursor);
        boolean hasMore = list.size() > pageSize;
        int limit = hasMore ? pageSize : list.size();
        var items = list.subList(0, limit).stream()
                .map(m -> {
                    long peerUserId = m.getUserIdLow().equals(userId) ? m.getUserIdHigh() : m.getUserIdLow();
                    return new MatchItem(m.getId(), peerUserId, m.getMatchedAt().toEpochMilli(), m.getSource());
                })
                .toList();
        return new ListResponse(items, hasMore ? list.get(pageSize - 1).getId() : null, hasMore);
    }

    // ── Visit ──

    /** 访问记录 */
    @PostMapping("/visits")
    public void recordVisit(@RequestBody RecordVisitRequest req) {
        matchService.recordVisit(req.fromUserId, req.toUserId, req.fromUserType, req.source);
    }

    /** 谁看过我 */
    @GetMapping("/users/{userId}/visitors")
    public ListResponse visitors(@PathVariable Long userId,
                                  @RequestParam(defaultValue = "20") int pageSize,
                                  @RequestParam(required = false) Long cursor) {
        var list = matchService.listVisitors(userId, pageSize, cursor);
        boolean hasMore = list.size() > pageSize;
        int limit = hasMore ? pageSize : list.size();
        var items = list.subList(0, limit).stream()
                .map(v -> new VisitorItem(v.getId(), v.getFromUserId(), v.getFromUserType(), v.getVisitCount(), v.getVisitedAt().toEpochMilli()))
                .toList();
        return new ListResponse(items, hasMore ? list.get(pageSize - 1).getId() : null, hasMore);
    }

    // ── 请求/响应 DTO ──

    record SwipeRequest(long userId, long targetUserId, int direction, int targetUserType) {}
    record SuperHiRequest(long userId, long targetUserId, int targetUserType) {}
    record SwipeResponse(boolean matched, Long matchId) {}
    record ReplyLikeRequest(long userId, boolean likeBack) {}
    record ReplyLikeResponse(boolean matched, Long matchId) {}
    record RecordVisitRequest(long fromUserId, long toUserId, int fromUserType, int source) {}

    record SwipeItem(long id, long targetUserId, int direction, int targetUserType, long swipedAt) {}
    record LikedByItem(long id, long fromUserId, int fromUserType, long likedAt) {}
    record MatchItem(long matchId, long userId, long matchedAt, String source) {}
    record VisitorItem(long id, long fromUserId, int fromUserType, int visitCount, long visitedAt) {}
    record ListResponse(List<?> items, Long nextCursor, boolean hasMore) {}
    record WhoLikedMeResponse(List<LikedByItem> items, Long nextCursor, boolean hasMore, int totalCount) {}

    record GetFeedResponse(List<CardItem> cards, boolean exhausted) {}
    record CardItem(long targetUserId, int targetUserType) {}

    record QuotaResponse(int rightSwipeUsed, int cardsUsed, int superHiUsed,
                         int superHiFreeLimit, int superHiCoinPrice, String tier) {}
}
