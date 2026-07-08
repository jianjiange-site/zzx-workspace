package com.dating.server.match.service.impl;

import com.dating.server.match.constant.CacheKeys;
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
import com.dating.server.match.service.DhDelayedMatchService;
import com.dating.server.match.service.MatchService;
import com.dating.server.match.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 匹配服务实现 —— 划卡、配额、匹配、互动记录
 *
 * PRD 7.6 并发设计：
 * - 每个 (userId, targetUserId) 用 Redisson 锁串行化
 * - Redis HINCRBY 原子扣减配额，超额后 HINCRBY -1 回滚
 * - swipe 接口同步 SADD match:swiped:<userId> 供消费阶段二次过滤
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final SwipeHistoryManager swipeHistoryManager;
    private final LikeRecordManager likeRecordManager;
    private final MatchManager matchManager;
    private final MatchOutboxManager matchOutboxManager;
    private final VisitRecordManager visitRecordManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final QuotaService quotaService;
    private final DhDelayedMatchService dhDelayedMatchService;

    /** Redisson 锁等待时间（秒） */
    private static final long LOCK_WAIT_SECONDS = 3;
    /** Redisson 锁持有时间（秒） */
    private static final long LOCK_LEASE_SECONDS = 5;

    @Override
    public SwipeResult swipe(Long userId, Long targetUserId, int direction, int targetUserType) {
        // ── 校验 ──
        if (userId.equals(targetUserId)) {
            throw new BizException(ErrorCodes.SWIPE_SELF, "不能划自己");
        }
        if (direction != Constants.SWIPE_LEFT && direction != Constants.SWIPE_RIGHT
                && direction != Constants.SWIPE_SUPER_HI) {
            throw new BizException(ErrorCodes.INVALID_DIRECTION, "无效划卡方向: " + direction);
        }

        // Super Hi 不走普通 swipe 流程
        if (direction == Constants.SWIPE_SUPER_HI) {
            return superHi(userId, targetUserId, targetUserType);
        }

        // ── 并发锁：防止同一 (userId, targetUserId) 重复处理 ──
        RLock lock = redissonClient.getLock(CacheKeys.swipeLock(userId, targetUserId));
        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                throw new BizException(ErrorCodes.CONCURRENT_SWIPE, "操作太频繁，请重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCodes.CONCURRENT_SWIPE, "获取锁被中断");
        }

        try {
            return doSwipe(userId, targetUserId, direction, targetUserType);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 内部划卡逻辑（在 Redisson 锁保护内执行）
     */
    private SwipeResult doSwipe(Long userId, Long targetUserId, int direction, int targetUserType) {
        // ── 幂等检查 ──
        SwipeHistory existing = swipeHistoryManager.getByUserTarget(userId, targetUserId);
        if (existing != null) {
            if (existing.getDirection() == Constants.SWIPE_LEFT && direction != Constants.SWIPE_LEFT) {
                // 之前左滑过，升级为右滑
                swipeHistoryManager.updateDirection(existing.getId(), direction);
            } else {
                log.warn("重复划卡: userId={}, targetUserId={}", userId, targetUserId);
                // 幂等返回上次结果（不扣配额）
                return checkExistingMatch(userId, targetUserId);
            }
        }

        // ── 配额检查 + 扣减（左滑只扣卡片，右滑扣卡片+右划） ──
        String tier = quotaService.getUserTier(userId);

        // 先扣减卡片（所有方向都消耗）
        try {
            quotaService.checkCard(userId, tier);
        } catch (BizException e) {
            throw e;
        }
        quotaService.deductCard(userId);

        if (direction == Constants.SWIPE_RIGHT) {
            try {
                quotaService.checkRightSwipe(userId, tier);
            } catch (BizException e) {
                quotaService.rollbackCard(userId); // 卡片回滚
                throw e;
            }
            quotaService.deductRightSwipe(userId);
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
                // 并发重复（锁兜底了，但 ON CONFLICT 二次防御）
                log.warn("并发重复划卡: userId={}, targetUserId={}", userId, targetUserId);
                return checkExistingMatch(userId, targetUserId);
            }
        }

        // ── SADD 到 swiped SET（消费阶段二次过滤用） ──
        stringRedisTemplate.opsForSet().add(
                CacheKeys.swiped(userId), String.valueOf(targetUserId));

        // ── 右滑：记录 like + 匹配检测 ──
        if (direction == Constants.SWIPE_RIGHT) {
            // 记录 like（幂等：已存在则跳过）
            LikeRecord like = likeRecordManager.getByFromTo(userId, targetUserId);
            if (like == null) {
                like = new LikeRecord();
                like.setFromUserId(userId);
                like.setToUserId(targetUserId);
                like.setFromUserType(Constants.USER_TYPE_BH);
                like.setSource(Constants.LIKE_SOURCE_SWIPE);
                likeRecordManager.insert(like);
            }

            // 双向检测：对方是否也喜欢我
            LikeRecord reciprocal = likeRecordManager.getByFromTo(targetUserId, userId);
            if (reciprocal != null) {
                // BH 互划 → 立即 match
                if (targetUserType == Constants.USER_TYPE_BH) {
                    return createMatch(userId, targetUserId, Constants.MATCH_SOURCE_SWIPE);
                }
                // 对方是 DH 且已右划过我（异常情况，DH 不应该主动右划 BH）
                // 仍触发 match
                return createMatch(userId, targetUserId, Constants.MATCH_SOURCE_SWIPE);
            }

            // ── 对方是 DH 且未右划过我 → 延迟匹配 ──
            if (targetUserType == Constants.USER_TYPE_DH) {
                dhDelayedMatchService.scheduleDelayedMatch(
                        userId, targetUserId, Constants.MATCH_SOURCE_SWIPE);
                // 不立即 match，但返回给前端"已喜欢"
            }
        }

        return new SwipeResult(false, null);
    }

    /**
     * Super Hi —— 付费硬匹配
     *
     * 无视对方意愿，立即创建 match。
     * BH/DH 都立即匹配（PRD 5.1 矩阵确认）。
     *
     * 扣费逻辑：
     *   1. 先检查订阅赠送的 Super Hi 次数
     *   2. 如果赠送次数用完，尝试扣金币 (100)
     *   3. 都失败 → 抛 INSUFFICIENT_COINS
     */
    private SwipeResult superHi(Long userId, Long targetUserId, int targetUserType) {
        // ── 幂等检查 ──
        RLock lock = redissonClient.getLock(CacheKeys.swipeLock(userId, targetUserId));
        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                throw new BizException(ErrorCodes.CONCURRENT_SWIPE, "操作太频繁，请重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCodes.CONCURRENT_SWIPE, "获取锁被中断");
        }

        try {
            // 幂等：已划过直接返回
            SwipeHistory existing = swipeHistoryManager.getByUserTarget(userId, targetUserId);
            if (existing != null) {
                // 检查是否已 match
                long low = Math.min(userId, targetUserId);
                long high = Math.max(userId, targetUserId);
                Match m = matchManager.getByUserPair(low, high);
                if (m != null) {
                    return new SwipeResult(true, m.getId());
                }
                // 已划过但未 match → 可能之前是左滑，允许升级
                if (existing.getDirection() != Constants.SWIPE_LEFT) {
                    log.warn("重复 Super Hi: userId={}, targetUserId={}", userId, targetUserId);
                    return new SwipeResult(false, null);
                }
            }

            // ── 扣配额 ──
            String tier = quotaService.getUserTier(userId);

            // 扣卡片配额
            try {
                quotaService.checkCard(userId, tier);
            } catch (BizException e) {
                throw e;
            }
            quotaService.deductCard(userId);

            // 扣右划配额（Super Hi 也消耗 1 次右划）
            try {
                quotaService.checkRightSwipe(userId, tier);
            } catch (BizException e) {
                quotaService.rollbackCard(userId);
                throw e;
            }
            quotaService.deductRightSwipe(userId);

            // 扣 Super Hi（赠送次数或金币）
            boolean deducted = false;
            if (quotaService.checkSuperHi(userId, tier)) {
                quotaService.deductSuperHi(userId);
                deducted = true;
            } else if (quotaService.canCoinBuySuperHi(userId)) {
                // 金币购买（实际由 payment-service 处理）
                deducted = true; // TODO: 调 payment-service.consumeCoins
            }

            if (!deducted) {
                // 回滚已扣配额
                quotaService.rollbackRightSwipe(userId);
                quotaService.rollbackCard(userId);
                throw new BizException(ErrorCodes.INSUFFICIENT_COINS,
                        "Super Hi 次数不足，可用 100 金币购买");
            }

            // ── 记录 swipe_history ──
            if (existing == null) {
                SwipeHistory history = new SwipeHistory();
                history.setUserId(userId);
                history.setTargetUserId(targetUserId);
                history.setTargetUserType(targetUserType);
                history.setDirection(Constants.SWIPE_SUPER_HI);
                swipeHistoryManager.insert(history);
            } else {
                // 之前左滑过，升級
                swipeHistoryManager.updateDirection(existing.getId(), Constants.SWIPE_SUPER_HI);
            }

            // ── SADD 到 swiped SET ──
            stringRedisTemplate.opsForSet().add(
                    CacheKeys.swiped(userId), String.valueOf(targetUserId));

            // ── 立即创建 match（Super Hi 跳过互检、跳过延迟） ──
            return createMatch(userId, targetUserId, Constants.MATCH_SOURCE_SUPER_HI);

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 检查重复划卡时是否已有 match
     */
    private SwipeResult checkExistingMatch(Long userId, Long targetUserId) {
        long low = Math.min(userId, targetUserId);
        long high = Math.max(userId, targetUserId);
        Match m = matchManager.getByUserPair(low, high);
        if (m != null) {
            return new SwipeResult(true, m.getId());
        }
        return new SwipeResult(false, null);
    }

    // ── 以下为原有方法（listSwipes / whoLikedMe / replyLike / listMatches / recordVisit / listVisitors）──

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
            long low = Math.min(record.getFromUserId(), record.getToUserId());
            long high = Math.max(record.getFromUserId(), record.getToUserId());
            Match existingMatch = matchManager.getByUserPair(low, high);
            if (existingMatch != null) {
                return new ReplyLikeResult(true, existingMatch.getId());
            }
            SwipeResult result = createMatch(record.getToUserId(), record.getFromUserId(),
                    Constants.MATCH_SOURCE_SWIPE);
            likeRecordManager.deleteByFromTo(record.getFromUserId(), record.getToUserId());
            return new ReplyLikeResult(true, result.matchId());
        } else {
            likeRecordManager.softDelete(likeRecordId);
            return new ReplyLikeResult(false, null);
        }
    }

    @Override
    public List<Match> listMatches(Long userId, int pageSize, Long cursor) {
        return matchManager.listByUser(userId, pageSize, cursor);
    }

    @Override
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

    /** 创建匹配 + outbox 事件（幂等） */
    private SwipeResult createMatch(Long userIdA, Long userIdB, String source) {
        long low = Math.min(userIdA, userIdB);
        long high = Math.max(userIdA, userIdB);

        // 幂等：已有匹配直接返回
        Match existing = matchManager.getByUserPair(low, high);
        if (existing != null) {
            log.warn("重复 match 创建尝试: pair=({}, {}), existingId={}, existingSource={}, newSource={}",
                    low, high, existing.getId(), existing.getSource(), source);
            return new SwipeResult(true, existing.getId());
        }

        Match match = new Match();
        match.setUserIdLow(low);
        match.setUserIdHigh(high);
        match.setMatchedAt(Instant.now());
        match.setSource(source);
        matchManager.insert(match);

        // 清理双向 like_record（升级为 match 后暗恋关系失效，PRD 5.3）
        likeRecordManager.deleteByFromTo(userIdA, userIdB);
        likeRecordManager.deleteByFromTo(userIdB, userIdA);

        // 写 outbox（MQ 事件回调用：建会话、系统消息、DH 开场白）
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
