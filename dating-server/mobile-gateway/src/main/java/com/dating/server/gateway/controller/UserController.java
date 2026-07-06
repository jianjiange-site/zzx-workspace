package com.dating.server.gateway.controller;

import com.dating.proto.user.*;
import com.dating.server.gateway.client.UserServiceClient;
import com.dating.server.gateway.common.ProtoJson;
import com.dating.server.gateway.common.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户资料与兴趣标签。所有接口从 JWT 中提取当前 userId。
 * <p>
 * 更新资料用 Map<String, Object> 接收（保持 API 柔性），只在转发给 user-service 时构造 proto。
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserServiceClient userServiceClient;

    /** 当前登录用户的完整资料（含兴趣标签、头像 key） */
    @GetMapping("/profile")
    public R<Map<String, Object>> getProfile(HttpServletRequest request) {
        long userId = (Long) request.getAttribute("userId");
        var profile = userServiceClient.getProfile(userId);
        return R.ok(ProtoJson.toMap(profile));
    }

    /** 查看其他用户的公开资料 */
    @GetMapping("/profile/{targetUserId}")
    public R<Map<String, Object>> getProfileByUserId(@PathVariable long targetUserId) {
        var profile = userServiceClient.getProfile(targetUserId);
        return R.ok(ProtoJson.toMap(profile));
    }

    /** 编辑资料：只传要改的字段即可，不传的保持不变 */
    @PutMapping("/profile")
    public R<Void> updateProfile(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long userId = (Long) request.getAttribute("userId");
        var req = UpdateProfileRequest.newBuilder()
                .setUserId(userId);
        if (body.containsKey("nickname")) req.setNickname((String) body.get("nickname"));
        if (body.containsKey("bio")) req.setBio((String) body.get("bio"));
        if (body.containsKey("gender")) req.setGender(toInt(body.get("gender")));
        if (body.containsKey("birthday")) req.setBirthday((String) body.get("birthday"));
        if (body.containsKey("profession")) req.setProfession((String) body.get("profession"));
        if (body.containsKey("education")) req.setEducation((String) body.get("education"));
        if (body.containsKey("height")) req.setHeight(toInt(body.get("height")));
        if (body.containsKey("preferredLocation")) req.setPreferredLocation((String) body.get("preferredLocation"));
        userServiceClient.updateProfile(userId, req.build());
        return R.ok();
    }

    /** 兴趣标签（IMAGE/TEXT 混合列表） */
    @GetMapping("/interests")
    public R<Map<String, Object>> getInterests(HttpServletRequest request) {
        long userId = (Long) request.getAttribute("userId");
        var resp = userServiceClient.getInterests(userId);
        return R.ok(ProtoJson.toMap(resp));
    }

    /** 全量替换兴趣标签（先删后插，不是增量） */
    @PutMapping("/interests")
    public R<Void> replaceInterests(HttpServletRequest request, @RequestBody List<InterestItem> items) {
        long userId = (Long) request.getAttribute("userId");
        userServiceClient.replaceInterests(userId, items);
        return R.ok();
    }

    /** 封禁状态查询：返回 true 表示当前用户已被封禁 */
    @GetMapping("/ban/check")
    public R<Boolean> checkBan(HttpServletRequest request) {
        long userId = (Long) request.getAttribute("userId");
        var resp = userServiceClient.checkBan(userId);
        return R.ok(resp.getBanned());
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }
}
