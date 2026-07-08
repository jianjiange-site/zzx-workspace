package com.dating.server.match.recommend;

import com.dating.proto.user.DhCandidate;
import com.dating.proto.user.NearbyUser;
import com.dating.server.match.client.UserServiceClient;
import com.dating.server.match.constant.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 候选召回（PRD 4.2.2）
 *
 * 两池独立召回：
 *   - DH 池：调 user-service.listDhCandidates（宽过滤，取 240）
 *   - BH 池：调 user-service.nearbyUsers（200km + 7天活跃，取 240）
 *
 * D0 与 D1 共用同一套召回流程，仅偏好来源不同。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateRecaller {

    private final UserServiceClient userServiceClient;

    /** 池容量 */
    static final int POOL_SIZE = 240;

    /** D0 默认阈值 */
    static final double D0_RADIUS_KM = 100;
    static final int D0_ACTIVE_DAYS = 7;
    static final int D0_AGE_WINDOW = 5;
    static final int D0_BEAUTY_WINDOW = 15;

    /** D1 默认阈值 */
    static final double D1_RADIUS_KM = 200;
    static final int D1_ACTIVE_DAYS = 7;

    /**
     * DH 池召回（D1 用宽过滤 + exclude）
     */
    public List<DhCandidate> recallDh(int targetGender, int ageMin, int ageMax,
                                       int beautyMin, int beautyMax,
                                       List<String> races, Set<Long> excludeUserIds) {
        return userServiceClient.listDhCandidates(
                targetGender, ageMin, ageMax, beautyMin, beautyMax,
                races, new ArrayList<>(excludeUserIds), POOL_SIZE);
    }

    /**
     * BH 池召回（D1：200km + 7天活跃）
     */
    public List<NearbyUser> recallBhD1(Long userId, int targetGender,
                                        Set<Long> excludeUserIds) {
        return userServiceClient.nearbyUsers(
                userId, targetGender,
                0, 999, 0, 999,
                Collections.emptyList(), D1_RADIUS_KM, D1_ACTIVE_DAYS,
                POOL_SIZE, new ArrayList<>(excludeUserIds));
    }

    /**
     * BH 池召回（D0：严格条件单次调用）
     */
    public List<NearbyUser> recallBhD0(Long userId, int targetGender,
                                        int userAge, int userBeauty, String userRace,
                                        Set<Long> excludeUserIds) {
        return userServiceClient.nearbyUsers(
                userId, targetGender,
                Math.max(0, userAge - D0_AGE_WINDOW),
                Math.min(99, userAge + D0_AGE_WINDOW),
                Math.max(0, userBeauty - D0_BEAUTY_WINDOW),
                Math.min(999, userBeauty + D0_BEAUTY_WINDOW),
                userRace != null ? List.of(userRace) : Collections.emptyList(),
                D0_RADIUS_KM, D0_ACTIVE_DAYS,
                POOL_SIZE, new ArrayList<>(excludeUserIds));
    }
}
