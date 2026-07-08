package com.dating.server.post.grpc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.proto.common.Result;
import com.dating.proto.post.*;
import com.dating.server.post.dto.PostCommentVO;
import com.dating.server.post.dto.PostVO;
import com.dating.server.post.entity.Post;
import com.dating.server.post.entity.PostComment;
import com.dating.server.post.entity.PostImage;
import com.dating.server.post.exception.BizException;
import com.dating.server.post.manager.PostCommentManager;
import com.dating.server.post.manager.PostManager;
import com.dating.server.post.mapper.PostCommentMapper;
import com.dating.server.post.mapper.PostMapper;
import com.dating.server.post.service.FeedService;
import com.dating.server.post.service.PostCommentService;
import com.dating.server.post.service.PostLikeService;
import com.dating.server.post.service.PostService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class PostGrpcService extends PostServiceGrpc.PostServiceImplBase {

    private final PostService postService;
    private final PostLikeService postLikeService;
    private final FeedService feedService;
    private final PostCommentService postCommentService;
    private final PostManager postManager;
    private final PostCommentManager postCommentManager;
    private final PostMapper postMapper;
    private final PostCommentMapper postCommentMapper;

    // ==================== Post CRUD ====================

    @Override
    public void createPost(com.dating.proto.post.CreatePostRequest request,
                           StreamObserver<CreatePostResponse> responseObserver) {
        try {
            com.dating.server.post.dto.CreatePostRequest dto = new com.dating.server.post.dto.CreatePostRequest();
            dto.setContent(request.getContent());
            dto.setType(request.getType());
            dto.setVisibility(request.getVisibility());
            dto.setTopic(request.getTopic());
            dto.setAllowComment(request.getAllowComment());
            dto.setUserType(request.getUserType());

            if (request.getImagesCount() > 0) {
                List<com.dating.server.post.dto.CreatePostRequest.ImageItem> images =
                        request.getImagesList().stream().map(img -> {
                            var item = new com.dating.server.post.dto.CreatePostRequest.ImageItem();
                            item.setObjectKey(img.getObjectKey());
                            item.setWidth(img.getWidth());
                            item.setHeight(img.getHeight());
                            return item;
                        }).collect(Collectors.toList());
                dto.setImages(images);
            }

            PostVO vo = postService.createPost(request.getUserId(), dto);

            responseObserver.onNext(CreatePostResponse.newBuilder()
                    .setPostId(vo.getId())
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("CreatePost error", e);
            responseObserver.onNext(CreatePostResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getPostDetail(GetPostDetailRequest request,
                              StreamObserver<PostInfo> responseObserver) {
        try {
            PostVO vo = postService.getPost(request.getPostId(), request.getCurrentUserId());
            boolean liked = postLikeService.isLiked(request.getPostId(), request.getCurrentUserId());

            responseObserver.onNext(toPostInfo(vo, liked));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetPostDetail error: postId={}", request.getPostId(), e);
            responseObserver.onNext(PostInfo.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listUserPosts(ListUserPostsRequest request,
                              StreamObserver<ListUserPostsResponse> responseObserver) {
        try {
            LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                    .eq(Post::getUserId, request.getUserId())
                    .eq(Post::getStatus, "PUBLISHED")
                    .orderByDesc(Post::getId);
            if (request.getCursor() > 0) {
                wrapper.lt(Post::getId, request.getCursor());
            }
            wrapper.last("LIMIT " + (request.getPageSize() + 1));

            List<Post> posts = postMapper.selectList(wrapper);
            boolean hasMore = posts.size() > request.getPageSize();
            if (hasMore) {
                posts = posts.subList(0, request.getPageSize());
            }

            List<PostInfo> items = posts.stream().map(post -> {
                List<PostImage> images = postManager.getImagesByPostId(post.getId());
                boolean liked = postLikeService.isLiked(post.getId(), request.getCurrentUserId());
                return toPostInfo(post, images, liked);
            }).collect(Collectors.toList());

            long nextCursor = items.isEmpty() ? 0 : items.get(items.size() - 1).getId();

            responseObserver.onNext(ListUserPostsResponse.newBuilder()
                    .addAllItems(items)
                    .setNextCursor(nextCursor)
                    .setHasMore(hasMore)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ListUserPosts error", e);
            responseObserver.onNext(ListUserPostsResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ==================== Like ====================

    @Override
    public void actionLike(ActionLikeRequest request,
                           StreamObserver<ActionLikeResponse> responseObserver) {
        try {
            if (request.getLike()) {
                postLikeService.like(request.getPostId(), request.getUserId());
            } else {
                postLikeService.unlike(request.getPostId(), request.getUserId());
            }

            responseObserver.onNext(ActionLikeResponse.newBuilder()
                    .setSuccess(true)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ActionLike error", e);
            responseObserver.onNext(ActionLikeResponse.newBuilder()
                    .setSuccess(false)
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ==================== Comment ====================

    @Override
    public void createComment(com.dating.proto.post.CreateCommentRequest request,
                              StreamObserver<CreateCommentResponse> responseObserver) {
        try {
            com.dating.server.post.dto.CreateCommentRequest dto = new com.dating.server.post.dto.CreateCommentRequest();
            dto.setContent(request.getContent());
            dto.setParentId(request.getParentId());

            PostCommentVO vo = postCommentService.createComment(
                    request.getPostId(), request.getUserId(), dto);

            responseObserver.onNext(CreateCommentResponse.newBuilder()
                    .setCommentId(vo.getId())
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("CreateComment error", e);
            responseObserver.onNext(CreateCommentResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listComments(ListCommentsRequest request,
                             StreamObserver<ListCommentsResponse> responseObserver) {
        try {
            LambdaQueryWrapper<PostComment> wrapper = new LambdaQueryWrapper<PostComment>()
                    .eq(PostComment::getPostId, request.getPostId())
                    .eq(PostComment::getStatus, "PUBLISHED")
                    .orderByAsc(PostComment::getId);
            if (request.getCursor() > 0) {
                wrapper.gt(PostComment::getId, request.getCursor());
            }
            wrapper.last("LIMIT " + (request.getPageSize() + 1));

            List<PostComment> comments = postCommentMapper.selectList(wrapper);
            boolean hasMore = comments.size() > request.getPageSize();
            if (hasMore) {
                comments = comments.subList(0, request.getPageSize());
            }

            List<CommentInfo> items = comments.stream()
                    .map(c -> CommentInfo.newBuilder()
                            .setId(c.getId())
                            .setPostId(c.getPostId() != null ? c.getPostId() : 0)
                            .setUserId(c.getUserId() != null ? c.getUserId() : 0)
                            .setParentId(c.getParentId() != null ? c.getParentId() : 0)
                            .setContent(c.getContent() != null ? c.getContent() : "")
                            .setCreatedAt(c.getCreatedAt() != null ? c.getCreatedAt().toEpochMilli() : 0)
                            .build())
                    .collect(Collectors.toList());

            long nextCursor = items.isEmpty() ? 0 : items.get(items.size() - 1).getId();

            responseObserver.onNext(ListCommentsResponse.newBuilder()
                    .addAllComments(items)
                    .setNextCursor(nextCursor)
                    .setHasMore(hasMore)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ListComments error", e);
            responseObserver.onNext(ListCommentsResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void deleteComment(com.dating.proto.post.DeleteCommentRequest request,
                              StreamObserver<DeleteCommentResponse> responseObserver) {
        try {
            postCommentService.deleteComment(request.getCommentId(), request.getUserId());

            responseObserver.onNext(DeleteCommentResponse.newBuilder()
                    .setSuccess(true)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("DeleteComment error", e);
            responseObserver.onNext(DeleteCommentResponse.newBuilder()
                    .setSuccess(false)
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ==================== Delete Post ====================

    @Override
    public void deletePost(com.dating.proto.post.DeletePostRequest request,
                           StreamObserver<DeletePostResponse> responseObserver) {
        try {
            postService.deletePost(request.getPostId(), request.getUserId());

            responseObserver.onNext(DeletePostResponse.newBuilder()
                    .setSuccess(true)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("DeletePost error", e);
            responseObserver.onNext(DeletePostResponse.newBuilder()
                    .setSuccess(false)
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ==================== Feed ====================

    @Override
    public void getRecommendFeed(GetRecommendFeedRequest request,
                                 StreamObserver<GetRecommendFeedResponse> responseObserver) {
        try {
            // 解析游标："offset"（当前简单 offset 分页）
            String cursor = request.getCursor();
            int offset = 0;
            if (cursor != null && !cursor.isEmpty() && !cursor.equals("0")) {
                offset = Integer.parseInt(cursor);
            }

            List<PostVO> feed = feedService.getRecommendFeed(
                    request.getUserId(), request.getPageSize(), offset, request.getViewerUserType());

            List<PostInfo> items = feed.stream()
                    .map(vo -> toPostInfo(vo, vo.getLiked() != null && vo.getLiked()))
                    .collect(Collectors.toList());

            int nextOffset = offset + items.size();
            boolean hasMore = items.size() >= request.getPageSize();

            responseObserver.onNext(GetRecommendFeedResponse.newBuilder()
                    .addAllItems(items)
                    .setNextCursor(String.valueOf(nextOffset))
                    .setHasMore(hasMore)
                    .setResult(success())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetRecommendFeed error", e);
            responseObserver.onNext(GetRecommendFeedResponse.newBuilder()
                    .setResult(error(500, e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ==================== 内部工具 ====================

    private PostInfo toPostInfo(PostVO vo, boolean liked) {
        PostInfo.Builder builder = PostInfo.newBuilder()
                .setId(vo.getId())
                .setUserId(vo.getUserId())
                .setContent(vo.getContent() != null ? vo.getContent() : "")
                .setType(vo.getType() != null ? vo.getType() : "")
                .setVisibility(vo.getVisibility() != null ? vo.getVisibility() : "")
                .setTopic(vo.getTopic() != null ? vo.getTopic() : "")
                .setAllowComment(vo.getAllowComment() != null ? vo.getAllowComment() : true)
                .setLikeCount(vo.getLikeCount() != null ? vo.getLikeCount() : 0)
                .setCommentCount(vo.getCommentCount() != null ? vo.getCommentCount() : 0)
                .setShareCount(vo.getShareCount() != null ? vo.getShareCount() : 0)
                .setViewCount(vo.getViewCount() != null ? vo.getViewCount() : 0)
                .setScore(vo.getScore() != null ? vo.getScore() : 0.0)
                .setLiked(liked)
                .setCreatedAt(vo.getCreatedAt() != null ? vo.getCreatedAt().toEpochMilli() : 0)
                .setUpdatedAt(vo.getUpdatedAt() != null ? vo.getUpdatedAt().toEpochMilli() : 0)
                .setUserType(vo.getUserType() != null ? vo.getUserType() : 0)
                .setResult(success());

        if (vo.getImages() != null) {
            for (PostVO.ImageVO img : vo.getImages()) {
                builder.addImages(ImageVO.newBuilder()
                        .setId(img.getId())
                        .setObjectKey(img.getObjectKey() != null ? img.getObjectKey() : "")
                        .setWidth(img.getWidth() != null ? img.getWidth() : 0)
                        .setHeight(img.getHeight() != null ? img.getHeight() : 0)
                        .setSortOrder(img.getSortOrder() != null ? img.getSortOrder() : 0)
                        .build());
            }
        }

        return builder.build();
    }

    private PostInfo toPostInfo(Post post, List<PostImage> images, boolean liked) {
        PostInfo.Builder builder = PostInfo.newBuilder()
                .setId(post.getId())
                .setUserId(post.getUserId())
                .setContent(post.getContent() != null ? post.getContent() : "")
                .setType(post.getType() != null ? post.getType() : "")
                .setVisibility(post.getVisibility() != null ? post.getVisibility() : "")
                .setTopic(post.getTopic() != null ? post.getTopic() : "")
                .setAllowComment(post.getAllowComment() != null ? post.getAllowComment() : true)
                .setLikeCount(post.getLikeCount() != null ? post.getLikeCount() : 0)
                .setCommentCount(post.getCommentCount() != null ? post.getCommentCount() : 0)
                .setShareCount(post.getShareCount() != null ? post.getShareCount() : 0)
                .setViewCount(post.getViewCount() != null ? post.getViewCount() : 0)
                .setScore(post.getScore() != null ? post.getScore() : 0.0)
                .setLiked(liked)
                .setCreatedAt(post.getCreatedAt() != null ? post.getCreatedAt().toEpochMilli() : 0)
                .setUpdatedAt(post.getUpdatedAt() != null ? post.getUpdatedAt().toEpochMilli() : 0)
                .setUserType(post.getUserType() != null ? post.getUserType() : 0)
                .setResult(success());

        if (images != null) {
            for (PostImage img : images) {
                builder.addImages(ImageVO.newBuilder()
                        .setId(img.getId())
                        .setObjectKey(img.getObjectKey() != null ? img.getObjectKey() : "")
                        .setWidth(img.getWidth() != null ? img.getWidth() : 0)
                        .setHeight(img.getHeight() != null ? img.getHeight() : 0)
                        .setSortOrder(img.getSortOrder() != null ? img.getSortOrder() : 0)
                        .build());
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
