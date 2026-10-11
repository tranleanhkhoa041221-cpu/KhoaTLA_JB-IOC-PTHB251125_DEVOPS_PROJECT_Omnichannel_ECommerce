package com.quickbite.edu.saga;

import com.quickbite.edu.entity.Order;
import com.quickbite.edu.entity.enums.OrderStatus;
import com.quickbite.edu.entity.OutboxEvent;
import com.quickbite.edu.entity.enums.OutboxStatus;
import com.quickbite.edu.event.OrderCancelledEvent;
import com.quickbite.edu.kafka.KafkaTopics;
import com.quickbite.edu.mapper.OrderMapper;
import com.quickbite.edu.repository.InventoryRepository;
import com.quickbite.edu.repository.OrderRepository;
import com.quickbite.edu.service.interfaces.InventoryService;
import com.quickbite.edu.service.impl.OrderPersistenceOps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saga Compensation Cron Job — tu dong phuc hoi trang thai nhat quan cua he thong.
 *
 * <p><b>Van de giai quyet:</b> Neu service crash sau khi da reserve inventory nhung
 * truoc khi luu Order vao DB (hoac truoc khi nhan duoc ket qua thanh toan), cac SKU
 * do van co reservedQty duong — bi "giu" vinh vien va khong the ban cho ai khac.
 *
 * <p><b>Giai phap:</b> Dinh ky quet cac don hang co trang thai {@code PENDING} ton tai
 * qua nguong thoi gian (mac dinh: 30 phut). Neu don hang PENDING qua lau
 * nghia la thanh toan da het han hoac chua duoc xu ly → tu dong huy don va
 * tra lai (release) ton kho tuong ung.
 *
 * <p><b>An toan (Idempotent):</b> Moi lan chay job, chi xu ly don hang con PENDING.
 * Don da CANCELLED hoac CONFIRMED se bi bo qua. Optimistic locking (@Version)
 * dam bao khong co race condition neu co nhieu instance service cung chay job.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCompensationJob {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final OrderPersistenceOps orderPersistenceOps;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    /**
     * Nguong thoi gian (phut) sau do don PENDING bi coi la "het han".
     * Mac dinh 30 phut — nen dat >= timeout cua VNPay (15 phut).
     */
    @Value("${app.saga.stale-order-threshold-minutes:30}")
    private int staleOrderThresholdMinutes;

    /**
     * Chay moi 5 phut. Co the chinh sua qua thuoc tinh:
     * {@code app.saga.compensation-cron} trong application.yml
     */
    @Scheduled(cron = "${app.saga.compensation-cron:0 */5 * * * *}")
    public void compensateStalePendingOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(staleOrderThresholdMinutes);
        List<Order> staleOrders = orderRepository.findStalePendingOrders(threshold);

        if (staleOrders.isEmpty()) {
            log.debug("[SagaCompensation] Khong co don hang PENDING qua han can xu ly.");
            return;
        }

        log.warn("[SagaCompensation] Phat hien {} don hang PENDING qua han (ngưỡng: {} phut). Bat dau compensation...",
                staleOrders.size(), staleOrderThresholdMinutes);

        int compensated = 0;
        int skipped = 0;

        for (Order order : staleOrders) {
            try {
                compensate(order);
                compensated++;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                // Don hang vua duoc cap nhat boi luong khac (VD: nguoi dung vua thanh toan xong)
                // → bo qua, khong can xu ly
                log.info("[SagaCompensation] Don hang orderCode={} da duoc cap nhat boi luong khac, bo qua.",
                        order.getOrderCode());
                skipped++;
            } catch (Exception e) {
                log.error("[SagaCompensation] Loi khi compensation don hang orderCode={}: {}",
                        order.getOrderCode(), e.getMessage(), e);
                skipped++;
            }
        }

        log.info("[SagaCompensation] Hoan tat: compensated={}, skipped={}", compensated, skipped);
    }

    /**
     * Thuc hien compensation cho 1 don hang PENDING qua han:
     * 1. Chuyen trang thai Order → CANCELLED (atomic voi Outbox event)
     * 2. Giai phong (release) ton kho da giu truoc do
     */
    private void compensate(Order order) {
        // Guard: kiem tra lai trang thai truoc khi xu ly (co the da thay doi giua 2 buoc)
        if (order.getStatus() != OrderStatus.PENDING) {
            log.debug("[SagaCompensation] Don hang orderCode={} khong con PENDING ({}), bo qua.",
                    order.getOrderCode(), order.getStatus());
            return;
        }

        log.warn("[SagaCompensation] Huy don hang qua han: orderCode={}, createdAt={}, customerId={}",
                order.getOrderCode(), order.getCreatedAt(), order.getCustomerId());

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason("[Auto] Don hang qua han thanh toan (" + staleOrderThresholdMinutes + " phut)");

        // Tao Outbox event de notify cac service khac (Notification, Payment)
        OrderCancelledEvent cancelledEvent = OrderCancelledEvent.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .customerId(order.getCustomerId())
                .reason(order.getCancelReason())
                .cancelledAt(LocalDateTime.now())
                .build();

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(order.getId()))
                .eventType(OrderCancelledEvent.class.getSimpleName())
                .topic(KafkaTopics.ORDER_CANCELLED)
                .payload(objectMapper.writeValueAsString(cancelledEvent))
                .status(OutboxStatus.PENDING)
                .build();

        // Atomic: cap nhat Order + ghi Outbox event trong 1 transaction
        orderPersistenceOps.updateStatusWithOutboxAndFlush(order, outboxEvent);

        // Release ton kho: tra lai so luong da giu cho cac SKU trong don hang
        Map<String, Integer> skuQuantities = new LinkedHashMap<>();
        order.getItems().forEach(item ->
                skuQuantities.merge(item.getSku(), item.getQuantity(), Integer::sum));
        inventoryService.releaseStock(skuQuantities);

        log.info("[SagaCompensation] Da huy va giai phong ton kho cho orderCode={}, skus={}",
                order.getOrderCode(), skuQuantities.keySet());
    }
}
