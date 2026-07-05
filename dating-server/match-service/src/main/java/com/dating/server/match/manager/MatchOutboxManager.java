package com.dating.server.match.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.match.entity.MatchOutbox;
import com.dating.server.match.mapper.MatchOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MatchOutboxManager {

    private final MatchOutboxMapper matchOutboxMapper;

    public void insert(MatchOutbox outbox) {
        matchOutboxMapper.insert(outbox);
    }

    /** 查询待重试的 outbox */
    public List<MatchOutbox> listPending(int limit) {
        return matchOutboxMapper.selectList(new LambdaQueryWrapper<MatchOutbox>()
                .eq(MatchOutbox::getStatus, "PENDING")
                .le(MatchOutbox::getNextRetryAt, Instant.now())
                .eq(MatchOutbox::getDeleted, false)
                .last("LIMIT " + limit));
    }

    public void markDone(Long id) {
        MatchOutbox update = new MatchOutbox();
        update.setId(id);
        update.setStatus("DONE");
        matchOutboxMapper.updateById(update);
    }

    public void markDead(Long id) {
        MatchOutbox update = new MatchOutbox();
        update.setId(id);
        update.setStatus("DEAD");
        matchOutboxMapper.updateById(update);
    }

    public void incrementAttempts(Long id, Instant nextRetryAt) {
        MatchOutbox update = new MatchOutbox();
        update.setId(id);
        // attempts 自增用原生 SQL 处理，这里先查询再更新
        MatchOutbox existing = matchOutboxMapper.selectById(id);
        if (existing != null) {
            update.setAttempts(existing.getAttempts() + 1);
            update.setNextRetryAt(nextRetryAt);
            matchOutboxMapper.updateById(update);
        }
    }
}
