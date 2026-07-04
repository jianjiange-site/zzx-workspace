package com.dating.server.user.grpc;

import com.dating.proto.common.Result;
import com.dating.proto.user.*;
import com.dating.server.user.constant.AppName;
import com.dating.server.user.entity.UserInfo;
import com.dating.server.user.manager.UserInfoManager;
import com.dating.server.user.service.UserProfileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserProfileGrpc extends UserProfileServiceGrpc.UserProfileServiceImplBase {

    private final UserProfileService userProfileService;
    private final UserInfoManager userInfoManager;
    private final ObjectMapper objectMapper;

    @Override
    public void getProfile(GetProfileRequest request, StreamObserver<UserProfile> responseObserver) {
        try {
            UserInfo user = userInfoManager.getCachedById(request.getUserId());
            if (user == null) {
                responseObserver.onNext(UserProfile.newBuilder()
                        .setResult(error(404, "用户不存在: " + request.getUserId()))
                        .build());
                responseObserver.onCompleted();
                return;
            }
            responseObserver.onNext(toUserProfile(user));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetProfile error: userId={}", request.getUserId(), e);
            responseObserver.onNext(UserProfile.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void batchGetProfiles(BatchGetProfilesRequest request,
                                 StreamObserver<BatchGetProfilesResponse> responseObserver) {
        try {
            List<Long> userIds = request.getUserIdsList();
            if (userIds.isEmpty()) {
                responseObserver.onNext(BatchGetProfilesResponse.newBuilder()
                        .setResult(success())
                        .build());
                responseObserver.onCompleted();
                return;
            }
            if (userIds.size() > 200) {
                responseObserver.onNext(BatchGetProfilesResponse.newBuilder()
                        .setResult(error(400, "批量查询不能超过200个"))
                        .build());
                responseObserver.onCompleted();
                return;
            }

            List<UserInfo> users = userInfoManager.getByIds(userIds);
            BatchGetProfilesResponse.Builder builder = BatchGetProfilesResponse.newBuilder()
                    .setResult(success());
            for (UserInfo user : users) {
                builder.putProfiles(user.getId(), toUserProfile(user));
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("BatchGetProfiles error", e);
            responseObserver.onNext(BatchGetProfilesResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void updateProfile(UpdateProfileRequest request,
                              StreamObserver<UpdateProfileResponse> responseObserver) {
        try {
            Long userId = request.getUserId();

            UserInfo user = userInfoManager.getById(userId);
            if (user == null) {
                responseObserver.onNext(UpdateProfileResponse.newBuilder()
                        .setSuccess(false)
                        .setResult(error(404, "用户不存在: " + userId))
                        .build());
                responseObserver.onCompleted();
                return;
            }

            UserInfo update = new UserInfo();
            update.setId(userId);

            if (request.getNickname() != null) update.setNickname(request.getNickname().trim());
            if (request.getGender() != 0) update.setGender(request.getGender());
            if (request.getBirthday() != null && !request.getBirthday().isEmpty())
                update.setBirthday(java.time.LocalDate.parse(request.getBirthday()));
            if (request.getBio() != null) update.setBio(request.getBio());
            if (request.getProfession() != null) update.setProfession(request.getProfession());
            if (request.getEducation() != null) update.setEducation(request.getEducation());
            if (request.getHeight() != 0) update.setHeight(request.getHeight());
            if (request.getPreferredLocation() != null) update.setPreferredLocation(request.getPreferredLocation());

            userInfoManager.updateById(update);

            responseObserver.onNext(UpdateProfileResponse.newBuilder()
                    .setSuccess(true)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("UpdateProfile error: userId={}", request.getUserId(), e);
            responseObserver.onNext(UpdateProfileResponse.newBuilder()
                    .setSuccess(false)
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getGender(GetGenderRequest request, StreamObserver<GetGenderResponse> responseObserver) {
        try {
            UserInfo user = userInfoManager.getCachedById(request.getUserId());
            if (user == null) {
                responseObserver.onNext(GetGenderResponse.newBuilder()
                        .setResult(error(404, "用户不存在: " + request.getUserId()))
                        .build());
                responseObserver.onCompleted();
                return;
            }
            responseObserver.onNext(GetGenderResponse.newBuilder()
                    .setGender(user.getGender() != null ? user.getGender() : 0)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetGender error: userId={}", request.getUserId(), e);
            responseObserver.onNext(GetGenderResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void batchGetGenders(BatchGetGendersRequest request,
                                StreamObserver<BatchGetGendersResponse> responseObserver) {
        try {
            List<Long> userIds = request.getUserIdsList();
            List<UserInfo> users = userInfoManager.getByIds(userIds);
            BatchGetGendersResponse.Builder builder = BatchGetGendersResponse.newBuilder()
                    .setResult(success());
            for (UserInfo user : users) {
                builder.putGenders(user.getId(), user.getGender() != null ? user.getGender() : 0);
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("BatchGetGenders error", e);
            responseObserver.onNext(BatchGetGendersResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getFriendUserIds(GetFriendUserIdsRequest request,
                                 StreamObserver<GetFriendUserIdsResponse> responseObserver) {
        // TODO: 从 im-service 或 relation 表查询好友列表
        // 当前返回空列表，等待 im-service 接入后实现
        responseObserver.onNext(GetFriendUserIdsResponse.newBuilder()
                .addAllUserIds(Collections.emptyList())
                .setResult(success())
                .build());
        responseObserver.onCompleted();
    }

    private UserProfile toUserProfile(UserInfo user) {
        UserProfile.Builder builder = UserProfile.newBuilder()
                .setUserId(user.getId())
                .setNickname(user.getNickname() != null ? user.getNickname() : "")
                .setGender(user.getGender() != null ? user.getGender() : 0)
                .setBirthday(user.getBirthday() != null ? user.getBirthday().toString() : "")
                .setAge(user.getAge() != null ? user.getAge() : 0)
                .setBio(user.getBio() != null ? user.getBio() : "")
                .setProfession(user.getProfession() != null ? user.getProfession() : "")
                .setEducation(user.getEducation() != null ? user.getEducation() : "")
                .setHeight(user.getHeight() != null ? user.getHeight() : 0)
                .setPreferredLocation(user.getPreferredLocation() != null ? user.getPreferredLocation() : "")
                .setPending(user.getPending() != null ? user.getPending() : false)
                .setRegulationStatus(user.getRegulationStatus() != null ? user.getRegulationStatus() : 0)
                .setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().toEpochMilli() : 0)
                .setUpdatedAt(user.getUpdatedAt() != null ? user.getUpdatedAt().toEpochMilli() : 0)
                .setResult(success());

        // 解析头像 JSON
        if (user.getCustomAvatar() != null) {
            try {
                Map<String, String> avatarMap = objectMapper.readValue(
                        user.getCustomAvatar(), new TypeReference<Map<String, String>>() {});
                if (avatarMap.get("originalKey") != null)
                    builder.setAvatarOriginal(avatarMap.get("originalKey"));
                if (avatarMap.get("minKey") != null)
                    builder.setAvatarMin(avatarMap.get("minKey"));
                if (avatarMap.get("midKey") != null)
                    builder.setAvatarMid(avatarMap.get("midKey"));
            } catch (Exception e) {
                log.warn("头像 JSON 解析失败: userId={}", user.getId(), e);
            }
        }

        return builder.build();
    }

    private Result success() {
        return Result.newBuilder().setCode(0).setMessage("ok").build();
    }

    private Result error(int code, String message) {
        return Result.newBuilder().setCode(code).setMessage(message).build();
    }
}
