package com.dating.server.match.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.match.entity.Match;
import com.dating.server.match.mapper.MatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MatchManager {

    private final MatchMapper matchMapper;

    /** 查出两人中已有的匹配 */
    public Match getByUserPair(Long userIdLow, Long userIdHigh) {
        return matchMapper.selectOne(new LambdaQueryWrapper<Match>()
                .eq(Match::getUserIdLow, userIdLow)
                .eq(Match::getUserIdHigh, userIdHigh)
                .eq(Match::getDeleted, false));
    }

    /** 匹配列表（带游标分页），对应用户 ID 需要上层补全为对方 */
    public List<Match> listByUser(Long userId, int pageSize, Long cursor) {
        LambdaQueryWrapper<Match> q = new LambdaQueryWrapper<Match>()
                .and(w -> w.eq(Match::getUserIdLow, userId)
                           .or()
                           .eq(Match::getUserIdHigh, userId))
                .eq(Match::getDeleted, false);
        if (cursor != null && cursor > 0) {
            q.lt(Match::getId, cursor);
        }
        q.orderByDesc(Match::getId)
         .last("LIMIT " + (pageSize + 1));
        return matchMapper.selectList(q);
    }

    public void insert(Match match) {
        matchMapper.insert(match);
    }

    /** 查询用户的所有 match（DH 计划 exclude 用） */
    public List<Match> listByUserId(Long userId) {
        return matchMapper.selectList(new LambdaQueryWrapper<Match>()
                .and(w -> w.eq(Match::getUserIdLow, userId)
                           .or().eq(Match::getUserIdHigh, userId))
                .eq(Match::getDeleted, false));
    }

    /** 按 ID 查询 */
    public Match getById(Long id) {
        return matchMapper.selectById(id);
    }
}
