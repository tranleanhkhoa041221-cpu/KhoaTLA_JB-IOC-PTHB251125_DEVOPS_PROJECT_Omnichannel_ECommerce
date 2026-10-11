package com.quickbite.edu.service.impl;

import com.quickbite.edu.entity.Order;
import com.quickbite.edu.entity.OutboxEvent;
import com.quickbite.edu.repository.OrderRepository;
import com.quickbite.edu.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;

/**
 * Nhom cac thao tac ghi Order + OutboxEvent trong CUNG 1 transaction (Outbox Pattern):
 * neu luu Order thanh cong nhung Kafka dang down, event van nam an toan trong DB
 * (bang outbox_events) va se duoc OutboxEventPublisher gui lai sau - khong bao gio
 * xay ra tinh trang "luu don hang thanh cong nhung quen khong bao Notification/Payment Service".
 */
@Component
@RequiredArgsConstructor
public class OrderPersistenceOps {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public Order saveNewOrderWithOutbox(Order order, Function<Order, OutboxEvent> outboxFactory) {
        // saveAndFlush de chac chan id (IDENTITY) da duoc gan TRUOC khi serialize payload.
        Order saved = orderRepository.saveAndFlush(order);
        OutboxEvent outboxEvent = outboxFactory.apply(saved);
        outboxEvent.setAggregateId(String.valueOf(saved.getId()));
        outboxEventRepository.save(outboxEvent);
        return saved;
    }

    @Transactional
    public Order updateStatusWithOutbox(Order order, OutboxEvent outboxEvent) {
        Order saved = orderRepository.save(order);
        if (outboxEvent != null) {
            outboxEventRepository.save(outboxEvent);
        }
        return saved;
    }

    @Transactional
    public Order updateStatusWithOutboxAndFlush(Order order, OutboxEvent outboxEvent) {
        Order saved = orderRepository.saveAndFlush(order);
        if (outboxEvent != null) {
            outboxEventRepository.saveAndFlush(outboxEvent);
        }
        return saved;
    }

    @Transactional
    public Order updateStatusAndFlush(Order order) {
        return orderRepository.saveAndFlush(order);
    }
}
