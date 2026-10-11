package com.quickbite.edu.controller;

import com.quickbite.edu.dto.request.SendTestNotificationRequest;
import com.quickbite.edu.dto.response.ApiResponse;
import com.quickbite.edu.dto.response.NotificationLogResponse;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.entity.enums.NotificationChannel;
import com.quickbite.edu.entity.enums.NotificationStatus;
import com.quickbite.edu.entity.enums.NotificationType;
import com.quickbite.edu.service.interfaces.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationLogController {

    private final NotificationService notificationService;

    /**
     * Gui thu 1 thong bao THAT (Email qua SMTP / SMS qua Twilio / Push mo phong) ma KHONG can
     * dung Kafka + Order Service + Payment Service - phuc vu test nhanh tren Postman va demo.
     */
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<NotificationLogResponse>> sendTest(
            @RequestBody @Valid SendTestNotificationRequest request
    ) {
        NotificationLogResponse response = notificationService.sendTest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Da gui thong bao thu", response));
    }

    @GetMapping("/logs/{id}")
    public ResponseEntity<ApiResponse<NotificationLogResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Lay nhat ky thong bao thanh cong", notificationService.getById(id)));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<PageResponse<NotificationLogResponse>>> search(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) NotificationType notificationType,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<NotificationLogResponse> response = notificationService.search(
                customerId, orderId, notificationType, channel, status, page, size
        );
        return ResponseEntity.ok(ApiResponse.success("Lay danh sach nhat ky thong bao thanh cong", response));
    }
}
