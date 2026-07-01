package com.dating.server.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dating.server.user.entity.UserInterest;

/**
 * 兴趣标签 Mapper
 * 查询按 userId，写入是先删后插（全量替换）
 */
public interface UserInterestMapper extends BaseMapper<UserInterest> {
}
