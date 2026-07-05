package com.dating.server.match.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.match.entity.VisitRecord;
import com.dating.server.match.mapper.VisitRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class
VisitRecordManager {

    private final VisitRecordMapper visitRecordMapper;

    /** 查某人对目标的访问记录 */
    public VisitRecord getByFromTo(Long fromUserId, Long toUserId) {
        return visitRecordMapper.selectOne(new LambdaQueryWrapper<VisitRecord>()
                .eq(VisitRecord::getFromUserId, fromUserId)
                .eq(VisitRecord::getToUserId, toUserId)
                .eq(VisitRecord::getDeleted, false));
    }

    /** 谁访问过我 */
    public List<VisitRecord> listVisitors(Long toUserId, int pageSize, Long cursor) {
        LambdaQueryWrapper<VisitRecord> q = new LambdaQueryWrapper<VisitRecord>()
                .eq(VisitRecord::getToUserId, toUserId)
                .eq(VisitRecord::getDeleted, false);
        if (cursor != null && cursor > 0) {
            q.lt(VisitRecord::getId, cursor);
        }
        q.orderByDesc(VisitRecord::getId)
         .last("LIMIT " + (pageSize + 1));
        return visitRecordMapper.selectList(q);
    }

    /** 首次访问，插入新记录 */
    public void insert(VisitRecord record) {
        record.setVisitedAt(Instant.now());
        record.setVisitCount(1);
        visitRecordMapper.insert(record);
    }

    /** 重复访问，增加计数 + 更新时间 */
    public void incrementVisit(VisitRecord existing) {
        VisitRecord update = new VisitRecord();
        update.setId(existing.getId());
        update.setVisitCount(existing.getVisitCount() + 1);
        update.setVisitedAt(Instant.now());
        visitRecordMapper.updateById(update);
    }
}
