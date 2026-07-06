package com.dating.server.gateway.client;

import com.dating.proto.user.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * user-service 的 gRPC 客户端封装。
 * <p>
 * 通过 @GrpcClient 注入 4 个服务的 blocking stub（身份/资料/封禁/兴趣），
 * 把 gateway controller 从 proto builder 样板代码中解放出来。
 * 每个方法只做两件事：build request → call stub，异常抛给全局处理器。
 */
@Component
public class UserServiceClient {

    @GrpcClient("user-service")
    private UserIdentityServiceGrpc.UserIdentityServiceBlockingStub identityStub;

    @GrpcClient("user-service")
    private UserProfileServiceGrpc.UserProfileServiceBlockingStub profileStub;

    @GrpcClient("user-service")
    private UserBanServiceGrpc.UserBanServiceBlockingStub banStub;

    @GrpcClient("user-service")
    private UserInterestServiceGrpc.UserInterestServiceBlockingStub interestStub;

    // ── 身份认证（3 种登录方式） ──

    public PhoneLoginResponse loginByPhone(String phone, String code) {
        var req = PhoneLoginRequest.newBuilder().setPhone(phone).setCode(code).build();
        return identityStub.registerOrLoginByPhone(req);
    }

    public ThirdPartyLoginResponse loginByThirdParty(String platform, String thirdPartyUserId, String deviceId) {
        var req = ThirdPartyLoginRequest.newBuilder()
                .setPlatform(platform)
                .setThirdPartyUserId(thirdPartyUserId)
                .setDeviceId(deviceId)
                .build();
        return identityStub.registerOrLoginByThirdParty(req);
    }

    public DeviceLoginResponse loginByDevice(String deviceId, String platform) {
        var req = DeviceLoginRequest.newBuilder()
                .setDeviceId(deviceId)
                .setPlatform(platform)
                .build();
        return identityStub.registerOrLoginByDevice(req);
    }

    // ── 用户资料 ──

    public UserProfile getProfile(long userId) {
        var req = GetProfileRequest.newBuilder().setUserId(userId).build();
        return profileStub.getProfile(req);
    }

    /** 更新资料：传入 UpdateProfileRequest，内部填入 userId */
    public boolean updateProfile(long userId, UpdateProfileRequest req) {
        var r = req.toBuilder().setUserId(userId).build();
        return profileStub.updateProfile(r).getSuccess();
    }

    // ── 封禁检查 ──

    public CheckBanResponse checkBan(long userId) {
        var req = CheckBanRequest.newBuilder().setUserId(userId).build();
        return banStub.checkBan(req);
    }

    // ── 兴趣标签 ──

    public GetInterestsResponse getInterests(long userId) {
        var req = GetInterestsRequest.newBuilder().setUserId(userId).build();
        return interestStub.getInterests(req);
    }

    /** 全量替换兴趣标签（先删后插） */
    public boolean replaceInterests(long userId, java.util.List<InterestItem> items) {
        var req = ReplaceInterestsRequest.newBuilder()
                .setUserId(userId)
                .addAllItems(items)
                .build();
        return interestStub.replaceInterests(req).getSuccess();
    }
}
