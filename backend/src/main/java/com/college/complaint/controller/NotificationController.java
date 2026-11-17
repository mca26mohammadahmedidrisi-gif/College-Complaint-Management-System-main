package com.college.complaint.controller;

import com.college.complaint.dto.NotificationView;
import com.college.complaint.service.NotificationService;
import com.college.complaint.utility.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ApiResponse<List<NotificationView>> list(Authentication authentication) {
        return ApiResponse.success("Notifications retrieved successfully",
                notifications.list(UUID.fromString(authentication.getName())));
    }

    @PutMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@PathVariable UUID notificationId, Authentication authentication) {
        notifications.markRead(UUID.fromString(authentication.getName()), notificationId);
        return ApiResponse.success("Notification marked as read", null);
    }
}
