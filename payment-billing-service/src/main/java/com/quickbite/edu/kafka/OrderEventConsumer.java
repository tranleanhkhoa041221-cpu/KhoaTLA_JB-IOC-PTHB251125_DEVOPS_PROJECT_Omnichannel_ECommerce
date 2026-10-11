package com.quickbite.edu.kafka;

import com.quickbite.edu.event.OrderCancelledEvent;
import com.quickbite.edu.event.OrderCreatedEvent;
import com.quickbite.edu.service.interfaces.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Lang nghe event tu Order & Inventory Service (Choreography Saga - khong co
 * 1 "nhac truong" trung tam, tung service tu quyet dinh phan viec cua minh
 * khi nhan duoc event phu hop):
 *  - order.created   -> khoi tao 1 giao dich thanh toan PENDING tuong ung.
 *  - order.cancelled -> huy giao dich thanh toan neu con dang PENDING.
 *
 * Ca 2 handler deu duoc thiet ke IDEMPOTENT (an toan khi Kafka gui lai event
 * trung do at-least-once delivery / consumer retry).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "payment-billing-service")
    public void onOrderCreated(String message) throws Exception {
        OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
        log.info("Nhan event order.created cho orderId={}, orderCode={}", event.getOrderId(), event.getOrderCode());
        paymentService.handleOrderCreated(event);
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "payment-billing-service")
    public void onOrderCancelled(String message) throws Exception {
        OrderCancelledEvent event = objectMapper.readValue(message, OrderCancelledEvent.class);
        log.info("Nhan event order.cancelled cho orderId={}, ly do={}", event.getOrderId(), event.getReason());
        paymentService.handleOrderCancelled(event);
    }
}
