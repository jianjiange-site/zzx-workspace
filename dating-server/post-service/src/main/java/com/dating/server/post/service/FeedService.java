package com.dating.server.post.service;

import com.dating.server.post.dto.PostVO;

import java.util.List;

/**
 * Feed 推荐服务
 *
 * 负责热门池打分、冷启动池维护、推荐流混排
 */
public interface FeedService {

    /**
     * 获取推荐 Feed 列表
     *
     * @param userId         当前用户 ID
     * @param pageSize       每页条数
     * @param offset         偏移量（游标）
     * @param viewerUserType 观看者类型：1=BH 2=DH 0=不限
     * @return 推荐帖子列表
     */
    List<PostVO> getRecommendFeed(Long userId, int pageSize, int offset, int viewerUserType);

    /**
     * 冷启动入池：新帖子发布后调用
     *
     * @param userType 发布者类型：1=BH 2=DH 0=不限
     */
    void addToColdPool(Long postId, long createdAtEpochMs, int userType);
}
