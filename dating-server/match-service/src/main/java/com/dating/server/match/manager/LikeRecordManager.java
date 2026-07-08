package com.dating.server.match.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.match.entity.LikeRecord;
import com.dating.server.match.mapper.LikeRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LikeRecordManager {

    private final LikeRecordMapper likeRecordMapper;

    public LikeRecord getById(Long id) {
        return likeRecordMapper.selectById(id);
    }

    /** 查某人的 like 记录（未删除） */
    public LikeRecord getByFromTo(Long fromUserId, Long toUserId) {
        return likeRecordMapper.selectOne(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getFromUserId, fromUserId)
                .eq(LikeRecord::getToUserId, toUserId)
                .eq(LikeRecord::getDeleted, false));
    }

    /** 谁喜欢过我（带游标分页） */
    public List<LikeRecord> listWhoLikedMe(Long toUserId, int pageSize, Long cursor) {
        LambdaQueryWrapper<LikeRecord> q = new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getToUserId, toUserId)
                .eq(LikeRecord::getDeleted, false);
        if (cursor != null && cursor > 0) {
            q.lt(LikeRecord::getId, cursor);
        }
        q.orderByDesc(LikeRecord::getId)
         .last("LIMIT " + (pageSize + 1));
        return likeRecordMapper.selectList(q);
    }

    /** 未处理的 like 数量（用于红点） */
    public int countUnhandled(Long toUserId, Long afterId) {
        return likeRecordMapper.selectCount(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getToUserId, toUserId)
                .eq(LikeRecord::getDeleted, false)
                .gt(LikeRecord::getId, afterId)).intValue();
    }

    /** 查询发给某目标的所有 like 记录（DH 计划 exclude 用） */
    public List<LikeRecord> listByToUser(Long toUserId) {
        return likeRecordMapper.selectList(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getToUserId, toUserId)
                .eq(LikeRecord::getDeleted, false));
    }

    /**
     * 批量检查哪些用户已经右划过当前用户（mutual like 检测）
     *
     * @param currentUserId 当前用户
     * @param candidateIds  候选用户 ID 列表
     * @return 已右划过当前用户的 candidateId 集合
     */
    public List<Long> listWhoLikedMeByCandidates(Long currentUserId, List<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) return List.of();
        return likeRecordMapper.selectObjs(new LambdaQueryWrapper<LikeRecord>()
                .select(LikeRecord::getFromUserId)
                .eq(LikeRecord::getToUserId, currentUserId)
                .eq(LikeRecord::getDeleted, false)
                .in(LikeRecord::getFromUserId, candidateIds))
                .stream()
                .map(id -> (Long) id)
                .collect(java.util.stream.Collectors.toList());
    }

    public void insert(LikeRecord record) {
        record.setLikedAt(Instant.now());
        likeRecordMapper.insert(record);
    }

    /** 软删除 like 记录（pass 操作） */
    public void softDelete(Long id) {
        likeRecordMapper.deleteById(id);
    }

    public void deleteByFromTo(Long fromUserId, Long toUserId) {
        likeRecordMapper.delete(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getFromUserId, fromUserId)
                .eq(LikeRecord::getToUserId, toUserId));
    }
}
