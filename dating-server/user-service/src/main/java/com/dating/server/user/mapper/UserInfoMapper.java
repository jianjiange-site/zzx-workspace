package com.dating.server.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dating.server.user.entity.UserInfo;

/**
 * 用户主资料 Mapper
 * 继承 BaseMapper 后自带单表 CRUD，无需写 SQL：
 *   - insert/deleteById/updateById/selectById/selectList
 *   复杂单表查询用 LambdaQueryWrapper，跨表在 Service 层拼装
 */
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}
