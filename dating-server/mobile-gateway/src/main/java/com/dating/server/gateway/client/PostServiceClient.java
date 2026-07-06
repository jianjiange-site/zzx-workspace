package com.dating.server.gateway.client;

import com.dating.proto.post.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * post-service 的 gRPC 客户端封装。
 * <p>
 * 覆盖动态 CRUD、点赞、评论、Feed 时间线等 9 个 RPC。
 * PostServiceGrpc 一个 service 包含所有接口，所以只需一个 stub。
 */
@Component
public class PostServiceClient {

    @GrpcClient("post-service")
    private PostServiceGrpc.PostServiceBlockingStub postStub;

    /** 创建动态：支持 TEXT/IMAGE 类型，可选图片列表 */
    public CreatePostResponse createPost(long userId, String content, String type, String visibility,
                                         String topic, boolean allowComment, List<ImageItem> images) {
        var req = CreatePostRequest.newBuilder()
                .setUserId(userId)
                .setContent(content)
                .setType(type)
                .setVisibility(visibility)
                .setTopic(topic != null ? topic : "")
                .setAllowComment(allowComment)
                .addAllImages(images)
                .build();
        return postStub.createPost(req);
    }

    /** 动态详情：currentUserId 用于判断是否已点赞 */
    public PostInfo getPostDetail(long postId, long currentUserId) {
        var req = GetPostDetailRequest.newBuilder()
                .setPostId(postId)
                .setCurrentUserId(currentUserId)
                .build();
        return postStub.getPostDetail(req);
    }

    /** 某用户的动态列表（游标分页） */
    public ListUserPostsResponse listUserPosts(long userId, long currentUserId, int pageSize, long cursor) {
        var req = ListUserPostsRequest.newBuilder()
                .setUserId(userId)
                .setCurrentUserId(currentUserId)
                .setPageSize(pageSize)
                .setCursor(cursor)
                .build();
        return postStub.listUserPosts(req);
    }

    /** 点赞/取消点赞：like=true 点赞，like=false 取消 */
    public ActionLikeResponse actionLike(long postId, long userId, boolean like) {
        var req = ActionLikeRequest.newBuilder()
                .setPostId(postId)
                .setUserId(userId)
                .setLike(like)
                .build();
        return postStub.actionLike(req);
    }

    /** 创建评论：parentId=0 为顶级评论，>0 为回复 */
    public CreateCommentResponse createComment(long postId, long userId, String content, long parentId) {
        var req = CreateCommentRequest.newBuilder()
                .setPostId(postId)
                .setUserId(userId)
                .setContent(content)
                .setParentId(parentId)
                .build();
        return postStub.createComment(req);
    }

    /** 评论列表（游标分页，按时间倒序） */
    public ListCommentsResponse listComments(long postId, int pageSize, long cursor) {
        var req = ListCommentsRequest.newBuilder()
                .setPostId(postId)
                .setPageSize(pageSize)
                .setCursor(cursor)
                .build();
        return postStub.listComments(req);
    }

    /** 删除评论：校验 userId 是否为作者 */
    public DeleteCommentResponse deleteComment(long commentId, long userId) {
        var req = DeleteCommentRequest.newBuilder()
                .setCommentId(commentId)
                .setUserId(userId)
                .build();
        return postStub.deleteComment(req);
    }

    /** 删除动态：校验 userId 是否为作者 */
    public DeletePostResponse deletePost(long postId, long userId) {
        var req = DeletePostRequest.newBuilder()
                .setPostId(postId)
                .setUserId(userId)
                .build();
        return postStub.deletePost(req);
    }

    /** 推荐 Feed（三步合并：缓存池 + 写扩散收件箱 + 关注池冷启动） */
    public GetRecommendFeedResponse getRecommendFeed(long userId, int pageSize, String cursor) {
        var req = GetRecommendFeedRequest.newBuilder()
                .setUserId(userId)
                .setPageSize(pageSize)
                .setCursor(cursor)
                .build();
        return postStub.getRecommendFeed(req);
    }
}
