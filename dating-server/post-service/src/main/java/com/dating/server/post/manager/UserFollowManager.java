package com.dating.server.post.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.server.post.entity.UserFollow;
import com.dating.server.post.mapper.UserFollowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户关注关系 Manager
 *
 * 功能：关注/取关/查粉丝/查关注
 * 目前直查 DB（读写都不高频），后续关注量大了可以加 Redis 缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserFollowManager {

    private final UserFollowMapper userFollowMapper;

    /**
     * 查用户的粉丝列表：返回关注了该用户的所有人
     * 用于写扩散：发帖后把帖子推给这些人
     */
    public List<Long> getFollowerIds(Long followeeId) {
        List<UserFollow> list = userFollowMapper.selectList(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFolloweeId, followeeId));
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(UserFollow::getFollowerId).collect(Collectors.toList());
    }

    /** 查用户的关注列表：该用户关注了哪些人 */
    public List<Long> getFolloweeIds(Long followerId) {
        List<UserFollow> list = userFollowMapper.selectList(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, followerId));
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(UserFollow::getFolloweeId).collect(Collectors.toList());
    }

    /** 检查是否已关注 */
    public boolean isFollowing(Long followerId, Long followeeId) {
        return userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, followerId)
                        .eq(UserFollow::getFolloweeId, followeeId)) > 0;
    }

    /** 关注用户（幂等：已关注则跳过） */
    public void follow(Long followerId, Long followeeId) {
        if (isFollowing(followerId, followeeId)) {
            return;
        }
        UserFollow uf = new UserFollow();
        uf.setFollowerId(followerId);
        uf.setFolloweeId(followeeId);
        userFollowMapper.insert(uf);
        log.info("关注成功: followerId={}, followeeId={}", followerId, followeeId);
    }

    /** 取消关注 */
    public void unfollow(Long followerId, Long followeeId) {
        userFollowMapper.delete(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, followerId)
                        .eq(UserFollow::getFolloweeId, followeeId));
        log.info("取消关注: followerId={}, followeeId={}", followerId, followeeId);
    }
}
