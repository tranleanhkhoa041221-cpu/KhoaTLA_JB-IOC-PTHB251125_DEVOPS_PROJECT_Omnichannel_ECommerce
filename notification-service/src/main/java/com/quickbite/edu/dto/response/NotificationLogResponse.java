package com.quickbite.edu.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLogResponse {
    private Long id;
    private Long customerId;
    private Long orderId;
    private String orderCode;
    private String notificationType;
    private String channel;
    private String recipient;
    private String subject;
    private String body;
    private String status;
    private String errorMessage;
    private String kafkaTopic;
    private String eventType;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
