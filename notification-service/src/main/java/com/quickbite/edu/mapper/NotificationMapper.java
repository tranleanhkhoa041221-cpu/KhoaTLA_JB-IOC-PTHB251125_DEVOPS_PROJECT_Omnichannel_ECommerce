package com.quickbite.edu.mapper;

import com.quickbite.edu.dto.response.NotificationLogResponse;
import com.quickbite.edu.entity.NotificationLog;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationLogResponse toResponse(NotificationLog log) {
        return NotificationLogResponse.builder()
                .id(log.getId())
                .customerId(log.getCustomerId())
                .orderId(log.getOrderId())
                .orderCode(log.getOrderCode())
                .notificationType(log.getNotificationType().name())
                .channel(log.getChannel().name())
                .recipient(log.getRecipient())
                .subject(log.getSubject())
                .body(log.getBody())
                .status(log.getStatus().name())
                .errorMessage(log.getErrorMessage())
                .kafkaTopic(log.getKafkaTopic())
                .eventType(log.getEventType())
                .sentAt(log.getSentAt())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
