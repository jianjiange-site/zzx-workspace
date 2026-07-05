package com.dating.server.user.service;

import com.dating.server.user.entity.UserInfo;

import java.util.List;

/**
 * 用户发现服务 — 供 match-service 召回候选用户
 */
public interface UserDiscoveryService {

    /** 查询 DH 候选（user_type=2） */
    List<UserInfo> listDhCandidates(int targetGender, int ageMin, int ageMax,
                                    int beautyMin, int beautyMax, List<String> races,
                                    List<Long> excludeUserIds, int limit);

    /** 查询附近 BH 用户（user_type=1） */
    List<UserInfo> nearbyUsers(Long selfUserId, int targetGender, int ageMin, int ageMax,
                                int beautyMin, int beautyMax, List<String> races,
                                double lat, double lng, double radiusKm,
                                int lastActiveDays, int limit,
                                List<Long> excludeUserIds);
}
