package com.dating.server.user.service.impl;

import com.dating.server.user.entity.UserInfo;
import com.dating.server.user.manager.UserInfoManager;
import com.dating.server.user.service.UserDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDiscoveryServiceImpl implements UserDiscoveryService {

    private final UserInfoManager userInfoManager;

    @Override
    public List<UserInfo> listDhCandidates(int targetGender, int ageMin, int ageMax,
                                            int beautyMin, int beautyMax, List<String> races,
                                            List<Long> excludeUserIds, int limit) {
        return userInfoManager.listDhCandidates(
                targetGender, ageMin, ageMax, beautyMin, beautyMax,
                races, excludeUserIds, limit);
    }

    @Override
    public List<UserInfo> nearbyUsers(Long selfUserId, int targetGender, int ageMin, int ageMax,
                                        int beautyMin, int beautyMax, List<String> races,
                                        double lat, double lng, double radiusKm,
                                        int lastActiveDays, int limit,
                                        List<Long> excludeUserIds) {
        return userInfoManager.nearbyUsers(
                selfUserId, targetGender, ageMin, ageMax, beautyMin, beautyMax,
                races, lat, lng, radiusKm, lastActiveDays, limit, excludeUserIds);
    }
}
