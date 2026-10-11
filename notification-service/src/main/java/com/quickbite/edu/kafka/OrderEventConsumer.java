package com.quickbite.edu.kafka;

import com.quickbite.edu.event.OrderCancelledEvent;
import com.quickbite.edu.event.OrderCreatedEvent;
import com.quickbite.edu.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * SUA LOI (integration review): truoc day 2 handler nay tu catch(Exception) roi CHI log, KHONG
 * rethrow -> ket hop voi "enable-auto-commit: false" + "listener.ack-mode: batch" (xem comment
 * application.yml), Spring Kafka van hieu nham la xu ly thanh cong va commit offset -> neu gui
 * Email/SMS that bai tam thoi (vd SMTP/Twilio timeout), khach hang se KHONG BAO GIO nhan duoc
 * thong bao do, khong co co che retry nao ca. Da bo try/catch de loi duoc NEM RA NGOAI cho
 * DefaultErrorHandler cua Spring Kafka retry (dong bo voi payment-billing-service/OrderEventConsumer).
 * Gui lai trung (do retry) la AN TOAN vi NotificationServiceImpl.dispatch() da chong trung bang
 * NotificationLog.idempotencyKey.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "notification-service")
    public void onOrderCreated(String message) {
        OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
        log.info("Notification nhan order.created orderId={}, orderCode={}", event.getOrderId(), event.getOrderCode());
        notificationService.handleOrderCreated(event, KafkaTopics.ORDER_CREATED);
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "notification-service")
    public void onOrderCancelled(String message) {
        OrderCancelledEvent event = objectMapper.readValue(message, OrderCancelledEvent.class);
        log.info("Notification nhan order.cancelled orderId={}", event.getOrderId());
        notificationService.handleOrderCancelled(event, KafkaTopics.ORDER_CANCELLED);
    }
}
