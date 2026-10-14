package com.quickbite.edu.kafka;

import com.quickbite.edu.event.PaymentRefundedEvent;
import com.quickbite.edu.event.PaymentResultEvent;
import com.quickbite.edu.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * SUA LOI (integration review): giong het OrderEventConsumer cung service - da bo try/catch
 * nuot loi de khong lam sai lech co che "chi commit offset khi xu ly thanh cong" da cau hinh
 * o application.yml. Xem giai thich chi tiet trong OrderEventConsumer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "notification-service")
    public void onPaymentCompleted(String message) {
        PaymentResultEvent event = objectMapper.readValue(message, PaymentResultEvent.class);
        log.info("Notification nhan payment.completed orderId={}", event.getOrderId());
        notificationService.handlePaymentCompleted(event, KafkaTopics.PAYMENT_COMPLETED);
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "notification-service")
    public void onPaymentFailed(String message) {
        PaymentResultEvent event = objectMapper.readValue(message, PaymentResultEvent.class);
        log.info("Notification nhan payment.failed orderId={}", event.getOrderId());
        notificationService.handlePaymentFailed(event, KafkaTopics.PAYMENT_FAILED);
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_REFUNDED, groupId = "notification-service")
    public void onPaymentRefunded(String message) {
        PaymentRefundedEvent event = objectMapper.readValue(message, PaymentRefundedEvent.class);
        log.info("Notification nhan payment.refunded orderId={}", event.getOrderId());
        notificationService.handlePaymentRefunded(event, KafkaTopics.PAYMENT_REFUNDED);
    }
}
