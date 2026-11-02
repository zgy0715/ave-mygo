package com.avemygo.reminder;

import com.avemygo.common.ApiResponse;
import com.avemygo.reminder.ReminderService.ReminderView;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开售提醒：订阅/取消挂在演出下（语义自然），我的提醒列表单独一个入口。
 * 三个接口都需要登录（SecurityConfig 对 /events/*\/reminder 单独放行 authenticated）。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService service;

    @PostMapping("/events/{eventId}/reminder")
    public ApiResponse<ReminderView> subscribe(@PathVariable Long eventId, Authentication auth) {
        return ApiResponse.ok(service.subscribe(Long.valueOf(auth.getName()), eventId));
    }

    @DeleteMapping("/events/{eventId}/reminder")
    public ApiResponse<Map<String, Boolean>> unsubscribe(@PathVariable Long eventId, Authentication auth) {
        service.unsubscribe(Long.valueOf(auth.getName()), eventId);
        return ApiResponse.ok(Map.of("removed", true));
    }

    @GetMapping("/reminders")
    public ApiResponse<List<ReminderView>> mine(Authentication auth) {
        return ApiResponse.ok(service.myReminders(Long.valueOf(auth.getName())));
    }
}
