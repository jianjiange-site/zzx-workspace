package com.dating.server.post.client;

import com.dating.proto.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * user-service gRPC 客户端
 *
 * 供 post-service 写扩散（getFriendUserIds）和 Feed 分桶（batchGetGenders）使用。
 * 所有方法带 fallback，远端异常时返回空/降级结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserClient {

    @GrpcClient("user-service")
    private UserProfileServiceGrpc.UserProfileServiceBlockingStub userProfileStub;

    /**
     * 查询粉丝列表（写扩散用）
     */
    public List<Long> getFriendUserIds(Long userId) {
        try {
            GetFriendUserIdsResponse resp = userProfileStub.getFriendUserIds(
                    GetFriendUserIdsRequest.newBuilder().setUserId(userId).build());
            return resp.getUserIdsList();
        } catch (Exception e) {
            log.error("user-service getFriendUserIds 失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 批量查询用户性别（Feed 分桶用）
     */
    public Map<Long, Integer> batchGetGenders(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        try {
            BatchGetGendersResponse resp = userProfileStub.batchGetGenders(
                    BatchGetGendersRequest.newBuilder()
                            .addAllUserIds(userIds)
                            .build());
            return resp.getGendersMap();
        } catch (Exception e) {
            log.error("user-service batchGetGenders 失败: userIds={}", userIds, e);
            return Collections.emptyMap();
        }
    }
}
