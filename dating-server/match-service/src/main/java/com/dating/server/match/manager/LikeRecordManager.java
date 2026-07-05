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
