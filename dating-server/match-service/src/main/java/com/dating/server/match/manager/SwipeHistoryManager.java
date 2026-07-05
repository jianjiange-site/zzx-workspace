package com.dating.server.match.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    public void insert(SwipeHistory history) {
        history.setSwipedAt(Instant.now());
        swipeHistoryMapper.insert(history);
    }

    /** 更新划卡方向（如从 right 升级为 super_hi） */
    public void updateDirection(Long id, int direction) {
        SwipeHistory update = new SwipeHistory();
        update.setId(id);
        update.setDirection(direction);
        swipeHistoryMapper.updateById(update);
    }
}
