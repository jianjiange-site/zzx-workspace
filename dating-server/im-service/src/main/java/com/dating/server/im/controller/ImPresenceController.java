package com.dating.server.im.controller;

import com.dating.server.im.service.ImPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ImPresenceController {

    private final ImPresenceService presenceService;

    /** 客户端心跳 */
    @PostMapping("/presence/heartbeat")
    public HeartbeatResponse heartbeat(@RequestBody HeartbeatRequest req) {
        presenceService.heartbeat(req.userId(), req.status());
        return new HeartbeatResponse(true);
    }

    /** 查询用户在线状态 */
    @GetMapping("/users/{userId}/presence")
    public PresenceResponse getPresence(@PathVariable Long userId) {
        ImPresenceService.PresenceInfo info = presenceService.getPresence(userId);
        return new PresenceResponse(info.userId(), info.online(), info.lastHeartbeatAt(), info.status());
    }

    record HeartbeatRequest(long userId, String status) {}
    record HeartbeatResponse(boolean success) {}
    record PresenceResponse(long userId, boolean online, long lastHeartbeatAt, String status) {}
}
