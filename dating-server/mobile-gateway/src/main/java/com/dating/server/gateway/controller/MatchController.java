package com.dating.server.gateway.controller;

import com.dating.server.gateway.client.MatchServiceClient;
import com.dating.server.gateway.common.ProtoJson;
import com.dating.server.gateway.common.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 匹配核心流程：划卡 → 双向喜欢 → 匹配 → 聊天入口。
 * <p>
 * direction: 1=LEFT 2=RIGHT 3=SUPER_HI
 * targetUserType: 1=BH(附近人) 2=DH(每日推荐)
 * source: 1=PROFILE_VIEW 2=DH_PLAN_ONLINE 3=DH_PLAN_OFFLINE
 */
@RestController
@RequestMapping("/api/v1/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchServiceClient matchServiceClient;

    /**
     * 拉今日 feed 卡片（首页推荐队列）
     * count: 移动端固定 5，最大 20
     * 返回 { cards: [{targetUserId, targetUserType, nickname, age, photoKeys, bio, distanceKm}], exhausted }
     */
    @GetMapping("/feed")
    public R<Map<String, Object>> getTodayFeed(HttpServletRequest request,
                                                @RequestParam(defaultValue = "5") int count) {
        long userId = (Long) request.getAttribute("userId");
        var resp = matchServiceClient.getTodayFeed(userId, count);
        return R.ok(ProtoJson.toMap(resp));
    }

    /** 划卡：右滑双向检测是否匹配，返回 matched + matchId */
    @PostMapping("/swipe")
    public R<Map<String, Object>> swipe(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long userId = (Long) request.getAttribute("userId");
        var resp = matchServiceClient.swipe(
                userId,
                longVal(body.get("targetUserId")),
                intVal(body.get("direction")),
                intVal(body.getOrDefault("targetUserType", 1)));
        return R.ok(ProtoJson.toMap(resp));
    }

    /** 划卡历史：含左滑/右滑/超级喜欢记录 */
    @GetMapping("/swipes")
    public R<Map<String, Object>> listSwipes(HttpServletRequest request,
                                              @RequestParam(defaultValue = "20") int pageSize,
                                              @RequestParam(defaultValue = "0") long cursor) {
        long userId = (Long) request.getAttribute("userId");
        var resp = matchServiceClient.listSwipes(userId, pageSize, cursor);
        return R.ok(ProtoJson.toMap(resp));
    }

    /** 喜欢过我的人（右滑/超级喜欢过当前用户的列表） */
    @GetMapping("/liked-me")
    public R<Map<String, Object>> getWhoLikedMe(HttpServletRequest request,
                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                 @RequestParam(defaultValue = "0") long cursor) {
        long userId = (Long) request.getAttribute("userId");
        var resp = matchServiceClient.getWhoLikedMe(userId, pageSize, cursor);
        return R.ok(ProtoJson.toMap(resp));
    }

    /** 回应 like：likeBack=true 进入匹配列表，false 忽略 */
    @PostMapping("/reply-like")
    public R<Map<String, Object>> replyLike(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long userId = (Long) request.getAttribute("userId");
        var resp = matchServiceClient.replyLike(
                userId,
                longVal(body.get("likeRecordId")),
                bool(body.getOrDefault("likeBack", false)));
        return R.ok(ProtoJson.toMap(resp));
    }

    /** 已匹配列表：从这里进入聊天 */
    @GetMapping("/matches")
    public R<Map<String, Object>> listMatches(HttpServletRequest request,
                                               @RequestParam(defaultValue = "20") int pageSize,
                                               @RequestParam(defaultValue = "0") long cursor) {
        long userId = (Long) request.getAttribute("userId");
        var resp = matchServiceClient.listMatches(userId, pageSize, cursor);
        return R.ok(ProtoJson.toMap(resp));
    }

    /** 访问他人主页（会被记录到对方的"谁看过我"列表） */
    @PostMapping("/visit")
    public R<Map<String, Object>> recordVisit(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long userId = (Long) request.getAttribute("userId");
        var resp = matchServiceClient.recordVisit(
                userId,
                longVal(body.get("toUserId")),
                intVal(body.getOrDefault("fromUserType", 1)),
                intVal(body.getOrDefault("source", 1)));
        return R.ok(ProtoJson.toMap(resp));
    }

    /** 谁看过我：含 visit_count（同一人重复访问会累积） */
    @GetMapping("/visitors")
    public R<Map<String, Object>> listVisitors(HttpServletRequest request,
                                                @RequestParam(defaultValue = "20") int pageSize,
                                                @RequestParam(defaultValue = "0") long cursor) {
        long userId = (Long) request.getAttribute("userId");
        var resp = matchServiceClient.listVisitors(userId, pageSize, cursor);
        return R.ok(ProtoJson.toMap(resp));
    }

    // ── 类型转换 helpers ──
    private int intVal(Object v) { return v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString()); }
    private long longVal(Object v) { return v instanceof Number n ? n.longValue() : Long.parseLong(v.toString()); }
    private boolean bool(Object v) { return v instanceof Boolean b && b; }
}
