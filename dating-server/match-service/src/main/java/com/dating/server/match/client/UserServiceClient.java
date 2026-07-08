package com.dating.server.match.client;

import com.dating.proto.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * user-service gRPC 客户端
 *
 * 封装用户资料查询和候选召回（listDhCandidates / nearbyUsers）。
 * 所有方法带 fallback，远端异常时返回空/降级结果，不影响主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    @GrpcClient("user-service")
    private UserProfileServiceGrpc.UserProfileServiceBlockingStub userProfileStub;

    /**
     * 批量查询用户资料
     */
    public Map<Long, UserProfile> batchGetProfiles(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        try {
            BatchGetProfilesResponse resp = userProfileStub.batchGetProfiles(
                    BatchGetProfilesRequest.newBuilder()
                            .addAllUserIds(userIds)
                            .build());
            return resp.getProfilesMap();
        } catch (Exception e) {
            log.error("user-service batchGetProfiles 失败: userIds={}", userIds, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 查询单个用户资料
     */
    public UserProfile getProfile(Long userId) {
        try {
            return userProfileStub.getProfile(
                    GetProfileRequest.newBuilder().setUserId(userId).build());
        } catch (Exception e) {
            log.error("user-service getProfile 失败: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 查询 DH 候选（PRD 4.1 / 4.2.2）
     *
     * @param targetGender    目标性别 1=MALE 2=FEMALE
     * @param ageMin          最小年龄
     * @param ageMax          最大年龄
     * @param beautyMin       最小颜值分
     * @param beautyMax       最大颜值分
     * @param races           优先人种列表（空=不限）
     * @param excludeUserIds  排除的用户 ID
     * @param limit           返回上限
     * @return DH 候选列表
     */
    public List<DhCandidate> listDhCandidates(int targetGender, int ageMin, int ageMax,
                                               int beautyMin, int beautyMax,
                                               List<String> races, List<Long> excludeUserIds,
                                               int limit) {
        try {
            ListDhCandidatesResponse resp = userProfileStub.listDhCandidates(
                    ListDhCandidatesRequest.newBuilder()
                            .setTargetGender(targetGender)
                            .setAgeMin(ageMin)
                            .setAgeMax(ageMax)
                            .setBeautyMin(beautyMin)
                            .setBeautyMax(beautyMax)
                            .addAllRaces(races != null ? races : Collections.emptyList())
                            .addAllExcludeUserIds(excludeUserIds != null ? excludeUserIds : Collections.emptyList())
                            .setLimit(limit)
                            .build());
            return resp.getCandidatesList();
        } catch (Exception e) {
            log.error("user-service listDhCandidates 失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询附近 BH 用户（PRD 4.1 / 4.2.2）
     *
     * @param userId              当前用户 ID
     * @param targetGender        目标性别
     * @param ageMin              最小年龄
     * @param ageMax              最大年龄
     * @param beautyMin           最小颜值分
     * @param beautyMax           最大颜值分
     * @param races               优先人种
     * @param radiusKm            搜索半径(km)
     * @param lastActiveWithinDays 最近活跃天数
     * @param limit               返回上限
     * @param excludeUserIds      排除的用户 ID
     * @return 附近用户列表
     */
    public List<NearbyUser> nearbyUsers(Long userId, int targetGender,
                                         int ageMin, int ageMax,
                                         int beautyMin, int beautyMax,
                                         List<String> races, double radiusKm,
                                         int lastActiveWithinDays, int limit,
                                         List<Long> excludeUserIds) {
        try {
            NearbyUsersResponse resp = userProfileStub.nearbyUsers(
                    NearbyUsersRequest.newBuilder()
                            .setUserId(userId)
                            .setTargetGender(targetGender)
                            .setAgeMin(ageMin)
                            .setAgeMax(ageMax)
                            .setBeautyMin(beautyMin)
                            .setBeautyMax(beautyMax)
                            .addAllRaces(races != null ? races : Collections.emptyList())
                            .setRadiusKm(radiusKm > 0 ? radiusKm : 100)
                            .setLastActiveWithinDays(lastActiveWithinDays > 0 ? lastActiveWithinDays : 7)
                            .setLimit(limit)
                            .addAllExcludeUserIds(excludeUserIds != null ? excludeUserIds : Collections.emptyList())
                            .build());
            return resp.getUsersList();
        } catch (Exception e) {
            log.error("user-service nearbyUsers 失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }
}
