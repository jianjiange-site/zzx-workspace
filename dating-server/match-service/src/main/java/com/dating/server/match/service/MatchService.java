package com.dating.server.match.service;

import com.dating.server.match.entity.LikeRecord;
import com.dating.server.match.entity.Match;
import com.dating.server.match.entity.SwipeHistory;
import com.dating.server.match.entity.VisitRecord;

import java.util.List;

public interface MatchService {

    /**
     * 划卡：
     * 1. 校验（不可自划、不可重复划）
     * 2. 记录 swipe_history
     * 3. 右滑/超级喜欢 → 记录 like + 双向匹配检测
     */
    SwipeResult swipe(Long userId, Long targetUserId, int direction, int targetUserType);

    /** 划卡历史 */
    List<SwipeHistory> listSwipes(Long userId, int pageSize, Long cursor);

    /** 喜欢过我的人 */
    List<LikeRecord> whoLikedMe(Long userId, int pageSize, Long cursor);

    /** 未处理的 like 总数 */
    int countUnhandledLikes(Long userId, Long afterId);

    /** 回应 like */
    ReplyLikeResult replyLike(Long userId, Long likeRecordId, boolean likeBack);

    /** 匹配列表 */
    List<Match> listMatches(Long userId, int pageSize, Long cursor);

    /** 访问记录 */
    void recordVisit(Long fromUserId, Long toUserId, int fromUserType, int source);

    /** 谁看过我 */
    List<VisitRecord> listVisitors(Long userId, int pageSize, Long cursor);

    // ── 返回值类型 ──

    record SwipeResult(boolean matched, Long matchId) {}

    record ReplyLikeResult(boolean matched, Long matchId) {}
}
