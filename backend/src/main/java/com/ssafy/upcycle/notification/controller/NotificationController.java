package com.ssafy.upcycle.notification.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.notification.dto.NotificationPageResponse;
import com.ssafy.upcycle.notification.dto.NotificationReadAllResponse;
import com.ssafy.upcycle.notification.dto.NotificationResponse;
import com.ssafy.upcycle.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/notifications", "/api/v1/notifications"})
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<NotificationPageResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(notificationService.list(userId(jwt), page, size));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> read(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long notificationId) {
        return ApiResponse.success(notificationService.read(userId(jwt), notificationId));
    }

    @PatchMapping("/read-all")
    public ApiResponse<NotificationReadAllResponse> readAll(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(notificationService.readAll(userId(jwt)));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
