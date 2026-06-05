package com.minduc.happabi.controller.notification;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.response.notification.NotificationListResponse;
import com.minduc.happabi.dto.response.notification.NotificationResponse;
import com.minduc.happabi.service.notification.INotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "APIs for user notifications")
public class NotificationController {

    private final INotificationService notificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<NotificationListResponse>> getMyNotifications() {
        return ResponseEntity.ok(BaseResponse.ok(notificationService.getMyNotifications()));
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<NotificationResponse>> markAsRead(@PathVariable UUID notificationId) {
        return ResponseEntity.ok(BaseResponse.ok(notificationService.markAsRead(notificationId)));
    }
}
