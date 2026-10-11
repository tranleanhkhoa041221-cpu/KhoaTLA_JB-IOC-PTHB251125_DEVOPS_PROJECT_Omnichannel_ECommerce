package com.quickbite.edu.service.impl;

import com.quickbite.edu.entity.OutboxEvent;
import com.quickbite.edu.entity.Payment;
import com.quickbite.edu.repository.OutboxEventRepository;
import com.quickbite.edu.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhom cac thao tac ghi Payment + OutboxEvent trong CUNG 1 transaction:
 * neu cap nhat trang thai Payment thanh cong nhung Kafka dang down, event
 * van nam an toan trong bang outbox_events va se duoc OutboxEventPublisher
 * gui lai sau - khong bao gio "mat" event payment.completed/payment.failed.
 */
@Component
@RequiredArgsConstructor
public class PaymentPersistenceOps {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public Payment updateStatusWithOutbox(Payment payment, OutboxEvent outboxEvent) {
        Payment saved = paymentRepository.save(payment);
        if (outboxEvent != null) {
            outboxEventRepository.save(outboxEvent);
        }
        return saved;
    }
}
