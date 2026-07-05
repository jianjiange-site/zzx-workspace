package com.dating.server.match.service.impl;

import com.dating.server.match.constant.Constants;
import com.dating.server.match.entity.LikeRecord;
import com.dating.server.match.entity.Match;
import com.dating.server.match.entity.MatchOutbox;
import com.dating.server.match.entity.SwipeHistory;
import com.dating.server.match.entity.VisitRecord;
import com.dating.server.match.exception.BizException;
import com.dating.server.match.exception.ErrorCodes;
import com.dating.server.match.manager.LikeRecordManager;
import com.dating.server.match.manager.MatchManager;
import com.dating.server.match.manager.MatchOutboxManager;
import com.dating.server.match.manager.SwipeHistoryManager;
import com.dating.server.match.manager.VisitRecordManager;
import com.dating.server.match.service.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final SwipeHistoryManager swipeHistoryManager;
    private final LikeRecordManager likeRecordManager;
    private final MatchManager matchManager;
    private final MatchOutboxManager matchOutboxManager;
    private final VisitRecordManager visitRecordManager;

    @Override
    @Transactional
    public SwipeResult swipe(Long userId, Long targetUserId, int direction, int targetUserType) {
        // ── 校验 ──
        if (userId.equals(targetUserId)) {
            throw new BizException(ErrorCodes.SWIPE_SELF, "不能划自己");
        }
        if (direction != Constants.SWIPE_LEFT && direction != Constants.SWIPE_RIGHT
                && direction != Constants.SWIPE_SUPER_HI) {
            throw new BizException(ErrorCodes.INVALID_DIRECTION, "无效划卡方向: " + direction);
        }

        // 重复划卡检测
        SwipeHistory existing = swipeHistoryManager.getByUserTarget(userId, targetUserId);
        if (existing != null) {
            if (existing.getDirection() == Constants.SWIPE_LEFT && direction != Constants.SWIPE_LEFT) {
                // 之前左滑过，可升级为右滑/超级喜欢
                swipeHistoryManager.updateDirection(existing.getId(), direction);
            } else {
                log.warn("重复划卡: userId={}, targetUserId={}", userId, targetUserId);
                throw new BizException(ErrorCodes.CONCURRENT_SWIPE, "已划过此人");
            }
        }

        // ── 记录 swipe_history ──
        if (existing == null) {
            SwipeHistory history = new SwipeHistory();
            history.setUserId(userId);
            history.setTargetUserId(targetUserId);
            history.setTargetUserType(targetUserType);
            history.setDirection(direction);
            try {
                swipeHistoryManager.insert(history);
            } catch (DuplicateKeyException e) {
                // UNIQUE(user_id, target_user_id) 约束，并发时第二个请求会冲突
                throw new BizException(ErrorCodes.CONCURRENT_SWIPE, "已划过此人");
            }
        }

        // ── 右滑/超级喜欢：记录 like + 匹配检测 ──
        if (direction == Constants.SWIPE_RIGHT || direction == Constants.SWIPE_SUPER_HI) {
            // 记录 like（幂等：已存在则跳过）
            LikeRecord like = likeRecordManager.getByFromTo(userId, targetUserId);
            if (like == null) {
                like = new LikeRecord();
                like.setFromUserId(userId);
                like.setToUserId(targetUserId);
                like.setFromUserType(Constants.USER_TYPE_BH);
                like.setSource(direction == Constants.SWIPE_SUPER_HI
                        ? Constants.LIKE_SOURCE_SUPER_HI
                        : Constants.LIKE_SOURCE_SWIPE);
                likeRecordManager.insert(like);
            }

            // 双向检测：对方是否也喜欢我
            LikeRecord reciprocal = likeRecordManager.getByFromTo(targetUserId, userId);
            if (reciprocal != null) {
                return createMatch(userId, targetUserId,
                        direction == Constants.SWIPE_SUPER_HI
                                ? Constants.MATCH_SOURCE_SUPER_HI
                                : Constants.MATCH_SOURCE_SWIPE);
            }
        }

        return new SwipeResult(false, null);
    }

    @Override
    public List<SwipeHistory> listSwipes(Long userId, int pageSize, Long cursor) {
        return swipeHistoryManager.listByUser(userId, pageSize, cursor);
    }

    @Override
    public List<LikeRecord> whoLikedMe(Long userId, int pageSize, Long cursor) {
        return likeRecordManager.listWhoLikedMe(userId, pageSize, cursor);
    }

    @Override
    public int countUnhandledLikes(Long userId, Long afterId) {
        return likeRecordManager.countUnhandled(userId, afterId);
    }

    @Override
    @Transactional
    public ReplyLikeResult replyLike(Long userId, Long likeRecordId, boolean likeBack) {
        LikeRecord record = likeRecordManager.getById(likeRecordId);
        if (record == null || record.getDeleted()) {
            throw new BizException(ErrorCodes.TARGET_NOT_FOUND, "like 记录不存在");
        }
        if (!record.getToUserId().equals(userId)) {
            throw new BizException(ErrorCodes.TARGET_NOT_FOUND, "无权操作此 like");
        }

        if (likeBack) {
            // 检查是否已匹配
            long low = Math.min(record.getFromUserId(), record.getToUserId());
            long high = Math.max(record.getFromUserId(), record.getToUserId());
            Match existingMatch = matchManager.getByUserPair(low, high);
            if (existingMatch != null) {
                // 已匹配则直接返回，避免重复创建
                return new ReplyLikeResult(true, existingMatch.getId());
            }
            // 创建匹配
            SwipeResult result = createMatch(record.getToUserId(), record.getFromUserId(),
                    Constants.MATCH_SOURCE_SWIPE);
            // 清理 like 记录
            likeRecordManager.deleteByFromTo(record.getFromUserId(), record.getToUserId());
            return new ReplyLikeResult(true, result.matchId());
        } else {
            // pass：软删除
            likeRecordManager.softDelete(likeRecordId);
            return new ReplyLikeResult(false, null);
        }
    }

    @Override
    public List<Match> listMatches(Long userId, int pageSize, Long cursor) {
        return matchManager.listByUser(userId, pageSize, cursor);
    }

    @Override
    @Transactional
    public void recordVisit(Long fromUserId, Long toUserId, int fromUserType, int source) {
        if (fromUserId.equals(toUserId)) return;

        VisitRecord existing = visitRecordManager.getByFromTo(fromUserId, toUserId);
        if (existing != null) {
            visitRecordManager.incrementVisit(existing);
        } else {
            VisitRecord record = new VisitRecord();
            record.setFromUserId(fromUserId);
            record.setToUserId(toUserId);
            record.setFromUserType(fromUserType);
            record.setSource(source);
            visitRecordManager.insert(record);
        }
    }

    @Override
    public List<VisitRecord> listVisitors(Long userId, int pageSize, Long cursor) {
        return visitRecordManager.listVisitors(userId, pageSize, cursor);
    }

    // ── 内部方法 ──

    /** 创建匹配 + outbox 事件 */
    private SwipeResult createMatch(Long userIdA, Long userIdB, String source) {
        long low = Math.min(userIdA, userIdB);
        long high = Math.max(userIdA, userIdB);

        // 幂等：已有匹配直接返回
        Match existing = matchManager.getByUserPair(low, high);
        if (existing != null) {
            return new SwipeResult(true, existing.getId());
        }

        Match match = new Match();
        match.setUserIdLow(low);
        match.setUserIdHigh(high);
        match.setMatchedAt(Instant.now());
        match.setSource(source);
        matchManager.insert(match);

        // 写 outbox（MQ 事件回调用）
        MatchOutbox outbox = new MatchOutbox();
        outbox.setMatchId(match.getId());
        outbox.setAction("ENSURE_CONVERSATION");
        outbox.setPayloadJson("{\"match_id\":" + match.getId() + "}");
        outbox.setAttempts(0);
        outbox.setNextRetryAt(Instant.now());
        outbox.setStatus("PENDING");
        matchOutboxManager.insert(outbox);

        log.info("新匹配 created: matchId={}, userIdA={}, userIdB={}, source={}",
                match.getId(), userIdA, userIdB, source);
        return new SwipeResult(true, match.getId());
    }
}
