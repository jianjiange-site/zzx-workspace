package com.dating.server.match.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.match.constant.Constants;
import com.dating.server.match.entity.SwipeHistory;
import com.dating.server.match.mapper.SwipeHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SwipeHistoryManager {

    private final SwipeHistoryMapper swipeHistoryMapper;

    /** 查某人对目标是否划过（防重复） */
    public SwipeHistory getByUserTarget(Long userId, Long targetUserId) {
        return swipeHistoryMapper.selectOne(new LambdaQueryWrapper<SwipeHistory>()
                .eq(SwipeHistory::getUserId, userId)
                .eq(SwipeHistory::getTargetUserId, targetUserId));
    }

    /** 划卡历史列表 */
    public List<SwipeHistory> listByUser(Long userId, int pageSize, Long cursor) {
        LambdaQueryWrapper<SwipeHistory> q = new LambdaQueryWrapper<SwipeHistory>()
                .eq(SwipeHistory::getUserId, userId);
        if (cursor != null && cursor > 0) {
            q.lt(SwipeHistory::getId, cursor);
        }
        q.orderByDesc(SwipeHistory::getId)
         .last("LIMIT " + (pageSize + 1));
        return swipeHistoryMapper.selectList(q);
    }

    /** 已划过的人 ID 列表（Redis 降级用） */
    public List<Long> listSwipedUserIds(Long userId) {
        return swipeHistoryMapper.selectObjs(new LambdaQueryWrapper<SwipeHistory>()
                .eq(SwipeHistory::getUserId, userId)
                .select(SwipeHistory::getTargetUserId)).stream()
                .map(id -> (Long) id)
                .toList();
    }

    /**
     * 查询某时间之后的右划/超级喜欢记录（D1 偏好建模用）
     */
    public List<SwipeHistory> listRightSwipesSince(Long userId, Instant since) {
        return swipeHistoryMapper.selectList(new LambdaQueryWrapper<SwipeHistory>()
                .eq(SwipeHistory::getUserId, userId)
                .in(SwipeHistory::getDirection, Constants.SWIPE_RIGHT, Constants.SWIPE_SUPER_HI)
                .ge(SwipeHistory::getSwipedAt, since)
                .orderByDesc(SwipeHistory::getSwipedAt));
    }

    public void insert(SwipeHistory history) {
        history.setSwipedAt(Instant.now());
        swipeHistoryMapper.insert(history);
    }

    /** 更新划卡方向（如从 right 升级为 super_hi） */
    /** 判断用户某时间后是否有划卡行为（D1 前置条件） */
    public boolean hasSwipeSince(Long userId, Instant since) {
        Long count = swipeHistoryMapper.selectCount(new LambdaQueryWrapper<SwipeHistory>()
                .eq(SwipeHistory::getUserId, userId)
                .ge(SwipeHistory::getSwipedAt, since));
        return count != null && count > 0;
    }

    /** 查询某时间后有划卡的去重用户列表（D1 cron 用） */
    public List<Long> listDistinctUserIdsSince(Instant since, int limit) {
        return swipeHistoryMapper.selectObjs(new LambdaQueryWrapper<SwipeHistory>()
                .select(SwipeHistory::getUserId)
                .ge(SwipeHistory::getSwipedAt, since)
                .groupBy(SwipeHistory::getUserId)
                .last("LIMIT " + limit)).stream()
                .map(id -> (Long) id)
                .collect(java.util.stream.Collectors.toList());
    }

    /** 游标分页版：基于 userId 游标分批查询（D1 cron 全量扫描用） */
    public List<Long> listDistinctUserIdsSince(Instant since, int limit, Long cursorUserId) {
        LambdaQueryWrapper<SwipeHistory> q = new LambdaQueryWrapper<SwipeHistory>()
                .select(SwipeHistory::getUserId)
                .ge(SwipeHistory::getSwipedAt, since)
                .groupBy(SwipeHistory::getUserId);
        if (cursorUserId != null) {
            q.gt(SwipeHistory::getUserId, cursorUserId);
        }
        q.orderByAsc(SwipeHistory::getUserId)
         .last("LIMIT " + limit);
        return swipeHistoryMapper.selectObjs(q).stream()
                .map(id -> (Long) id)
                .collect(java.util.stream.Collectors.toList());
    }

    public void updateDirection(Long id, int direction) {
        SwipeHistory update = new SwipeHistory();
        update.setId(id);
        update.setDirection(direction);
        swipeHistoryMapper.updateById(update);
    }
}
