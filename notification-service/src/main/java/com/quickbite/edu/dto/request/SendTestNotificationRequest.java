package com.quickbite.edu.dto.request;

import com.quickbite.edu.entity.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Cho phep gui thu 1 thong bao THAT (Email qua SMTP / SMS qua Twilio) ma KHONG can
 * dung Kafka + Order Service + Payment Service - phuc vu test nhanh tren Postman va demo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendTestNotificationRequest {

    @NotNull(message = "Kenh gui khong duoc de trong (EMAIL/SMS/PUSH)")
    private NotificationChannel channel;

    @NotBlank(message = "Nguoi nhan khong duoc de trong (email hoac so dien thoai)")
    private String recipient;

    private String subject;

    @NotBlank(message = "Noi dung khong duoc de trong")
    private String body;
}
