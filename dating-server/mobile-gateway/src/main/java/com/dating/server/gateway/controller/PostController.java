package com.dating.server.gateway.controller;

import com.dating.proto.post.ImageItem;
import com.dating.server.gateway.client.PostServiceClient;
import com.dating.server.gateway.common.ProtoJson;
import com.dating.server.gateway.common.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 动态 CRUD、点赞、评论、Feed 时间线。
 * <p>
 * 发动态时 images 可传空列表（纯文字动态）。
 * Feed 接口使用游标分页，cursor 格式由 post-service 定义（"recOffset:csOffset"）。
 */
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostServiceClient postServiceClient;

    /** 发布动态：TEXT 类型可不传 images，IMAGE 类型至少一张 */
    @PostMapping
    public R<Map<String, Object>> createPost(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long userId = (Long) request.getAttribute("userId");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> imageMaps = (List<Map<String, Object>>) body.getOrDefault("images", List.of());
        var images = imageMaps.stream().map(m -> ImageItem.newBuilder()
                .setObjectKey(str(m.get("objectKey")))
                .setWidth(intVal(m.get("width")))
                .setHeight(intVal(m.get("height")))
                .build()).toList();

        var resp = postServiceClient.createPost(
                userId,
                str(body.get("content")),
                str(body.getOrDefault("type", "TEXT")),
                str(body.getOrDefault("visibility", "PUBLIC")),
                str(body.get("topic")),
                bool(body.getOrDefault("allowComment", true)),
                images);
        return R.ok(ProtoJson.toMap(resp));
    }

    /** 动态详情：含当前用户是否已点赞 */
    @GetMapping("/{postId}")
    public R<Map<String, Object>> getPostDetail(@PathVariable long postId, HttpServletRequest request) {
        long userId = (Long) request.getAttribute("userId");
        var post = postServiceClient.getPostDetail(postId, userId);
        return R.ok(ProtoJson.toMap(post));
    }

    /** 删除自己的动态（校验 userId == 作者） */
    @DeleteMapping("/{postId}")
    public R<Void> deletePost(@PathVariable long postId, HttpServletRequest request) {
        long userId = (Long) request.getAttribute("userId");
        postServiceClient.deletePost(postId, userId);
        return R.ok();
    }

    /** 某用户的动态列表（游标分页） */
    @GetMapping("/user/{targetUserId}")
    public R<Map<String, Object>> listUserPosts(@PathVariable long targetUserId,
                                                 HttpServletRequest request,
                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                 @RequestParam(defaultValue = "0") long cursor) {
        long userId = (Long) request.getAttribute("userId");
        var resp = postServiceClient.listUserPosts(targetUserId, userId, pageSize, cursor);
        return R.ok(ProtoJson.toMap(resp));
    }

    /** 点赞/取消点赞：body 传 {"like": true} 或 {"like": false} */
    @PostMapping("/{postId}/like")
    public R<Void> actionLike(@PathVariable long postId, HttpServletRequest request,
                               @RequestBody Map<String, Object> body) {
        long userId = (Long) request.getAttribute("userId");
        boolean like = bool(body.getOrDefault("like", true));
        postServiceClient.actionLike(postId, userId, like);
        return R.ok();
    }

    /** 创建评论：parentId 不传或 0 为顶级评论 */
    @PostMapping("/{postId}/comments")
    public R<Map<String, Object>> createComment(@PathVariable long postId, HttpServletRequest request,
                                                  @RequestBody Map<String, Object> body) {
        long userId = (Long) request.getAttribute("userId");
        long parentId = longVal(body.getOrDefault("parentId", 0));
        var resp = postServiceClient.createComment(postId, userId, str(body.get("content")), parentId);
        return R.ok(ProtoJson.toMap(resp));
    }

    /** 评论列表：游标分页，最新在前 */
    @GetMapping("/{postId}/comments")
    public R<Map<String, Object>> listComments(@PathVariable long postId,
                                                @RequestParam(defaultValue = "20") int pageSize,
                                                @RequestParam(defaultValue = "0") long cursor) {
        var resp = postServiceClient.listComments(postId, pageSize, cursor);
        return R.ok(ProtoJson.toMap(resp));
    }

    /** 删除自己的评论（校验 userId == 作者） */
    @DeleteMapping("/comments/{commentId}")
    public R<Void> deleteComment(@PathVariable long commentId, HttpServletRequest request) {
        long userId = (Long) request.getAttribute("userId");
        postServiceClient.deleteComment(commentId, userId);
        return R.ok();
    }

    /** 推荐 Feed 时间线：cursor 格式 "recOffset:csOffset"，首次不传 */
    @GetMapping("/feed/recommend")
    public R<Map<String, Object>> getRecommendFeed(HttpServletRequest request,
                                                    @RequestParam(defaultValue = "20") int pageSize,
                                                    @RequestParam(defaultValue = "") String cursor) {
        long userId = (Long) request.getAttribute("userId");
        var resp = postServiceClient.getRecommendFeed(userId, pageSize, cursor);
        return R.ok(ProtoJson.toMap(resp));
    }

    // ── 类型转换 helpers（Map 入参 → 原始类型） ──
    private String str(Object v) { return v != null ? v.toString() : ""; }
    private int intVal(Object v) { return v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString()); }
    private long longVal(Object v) { return v instanceof Number n ? n.longValue() : Long.parseLong(v.toString()); }
    private boolean bool(Object v) { return v instanceof Boolean b && b; }
}
