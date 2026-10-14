package com.quickbite.edu.kafka;

import com.quickbite.edu.event.PaymentRefundedEvent;
import com.quickbite.edu.event.PaymentResultEvent;
import com.quickbite.edu.service.interfaces.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Lang nghe event tu Payment & Billing Service (Choreography Saga - khong co
 * 1 "nhac truong" trung tam, tung service tu quyet dinh phan viec cua minh
 * khi nhan duoc event phu hop):
 *  - payment.completed -> xac nhan don hang (OrderService.confirmOrder)
 *  - payment.failed    -> compensating transaction: tra lai kho + huy don
 *
 * SUA LOI NGHIEM TRONG (integration review): truoc day 2 handler nay tu catch(Exception) roi CHI
 * log, KHONG rethrow. Ket hop voi cau hinh "enable-auto-commit: false" + "listener.ack-mode: batch"
 * (co chu dich de Spring Kafka CHI commit offset SAU KHI listener method return thanh cong, tranh
 * mat event khi service crash giua chung - xem comment trong application.yml), viec nuot loi roi
 * return binh thuong o day khien Spring Kafka hieu lam la "xu ly thanh cong" -> VAN commit offset
 * nhu khong co gi xay ra, dong nghia voi MAT VINH VIEN event payment.completed/payment.failed neu
 * confirmOrder()/cancelOrderFromPaymentFailure() that bai tam thoi (vd deadlock, optimistic lock
 * conflict, DB tam gian doan) - don hang se ket KET MAC DINH o PENDING/khong duoc compensate,
 * KHONG CO CACH NAO TU PHUC HOI. Da bo try/catch de exception duoc NEM RA NGOAI container, cho
 * DefaultErrorHandler mac dinh cua Spring Kafka retry lai dung message do (dong bo voi cach
 * payment-billing-service/OrderEventConsumer da lam dung tu truoc).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "order-inventory-service")
    public void onPaymentCompleted(String message) {
        PaymentResultEvent event = objectMapper.readValue(message, PaymentResultEvent.class);
        log.info("Nhan event payment.completed cho orderId={}", event.getOrderId());
        orderService.confirmOrder(event.getOrderId(), event.getTransactionId());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "order-inventory-service")
    public void onPaymentFailed(String message) {
        PaymentResultEvent event = objectMapper.readValue(message, PaymentResultEvent.class);
        log.info("Nhan event payment.failed cho orderId={}, ly do={}", event.getOrderId(), event.getReason());
        orderService.cancelOrderFromPaymentFailure(event.getOrderId(), event.getReason());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_REFUNDED, groupId = "order-inventory-service")
    public void onPaymentRefunded(String message) {
        PaymentRefundedEvent event = objectMapper.readValue(message, PaymentRefundedEvent.class);
        log.info("Nhan event payment.refunded cho orderId={}, fullyRefunded={}", event.getOrderId(), event.isFullyRefunded());
        orderService.applyRefund(event.getOrderId(), event.isFullyRefunded(), event.getTotalRefundedAmount());
    }
}
