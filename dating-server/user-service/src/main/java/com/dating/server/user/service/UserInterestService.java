package com.dating.server.user.service;

import com.dating.server.user.dto.ReplaceInterestItem;

import java.util.List;

/**
 * 兴趣标签服务接口
 * 设计文档 §5.6：全量替换语义，不支持单条增删
 */
public interface UserInterestService {

    /**
     * 全量替换用户的兴趣标签
     * 事务内：DELETE 旧标签 → INSERT 新标签 → DEL 缓存
     *
     * @param userId   用户 ID
     * @param items    新标签列表（全量）
     */
    void replaceUserInterests(Long userId, List<ReplaceInterestItem> items);
}
