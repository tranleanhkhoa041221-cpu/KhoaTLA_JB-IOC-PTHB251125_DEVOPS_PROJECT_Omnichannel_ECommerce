package com.quickbite.edu.kafka;

import com.quickbite.edu.entity.OutboxEvent;
import com.quickbite.edu.entity.enums.OutboxStatus;
import com.quickbite.edu.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Xu ly publish 1 OutboxEvent DUY NHAT trong 1 transaction NGAN va DOC LAP
 * (Propagation.REQUIRES_NEW) - cung mau thiet ke voi order-inventory-service
 * (xem giai thich chi tiet o class cung ten ben do).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatchOps {

    private static final int MAX_RETRY = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchOne(Long eventId) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.PENDING) {
            return;
        }

        try {
            // SUA LOI TICH HOP (thieu timeout): truoc day .get() KHONG co gioi han thoi gian ->
            // phu thuoc vao gia tri mac dinh cua Kafka client (delivery.timeout.ms=120s). Neu
            // Kafka Broker down/khong phan hoi, 1 thread trong dispatchExecutor (chi co 8 thread,
            // xem OutboxEventPublisher) se bi CHIEM DUNG toi 120s CHO 1 EVENT DUY NHAT, dong thoi
            // giu luon 1 row-lock PESSIMISTIC_WRITE suot thoi gian do - khong dong bo voi muc do
            // "fail-fast" ma phan con lai cua du an da ap dung nhat quan (gRPC deadline 3s o
            // ProductGrpcClient cua order-inventory-service, Gateway httpclient response-timeout
            // 8s). Dat gioi han ro rang 5s (cung bac voi cac timeout khac trong du an) de giai
            // phong thread/row-lock nhanh hon, roi tang retryCount/chuyen FAILED (Outbox van dam
            // bao KHONG MAT event - event se duoc OutboxEventPublisher quet lai va thu dispatch
            // tiep o tick sau).
            kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            log.info("Da publish outbox event id={} type={} topic={}", event.getId(), event.getEventType(), event.getTopic());
        } catch (TimeoutException ex) {
            event.setRetryCount(event.getRetryCount() + 1);
            log.warn("Publish outbox event id={} qua 5s khong phan hoi (Kafka cham/down), se retry (lan {})",
                    event.getId(), event.getRetryCount(), ex);
            if (event.getRetryCount() >= MAX_RETRY) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("Outbox event id={} that bai sau {} lan retry - can kiem tra thu cong (dong vai tro Dead Letter)",
                        event.getId(), MAX_RETRY, ex);
            }
        } catch (Exception ex) {
            event.setRetryCount(event.getRetryCount() + 1);
            if (event.getRetryCount() >= MAX_RETRY) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("Outbox event id={} that bai sau {} lan retry - can kiem tra thu cong (dong vai tro Dead Letter)",
                        event.getId(), MAX_RETRY, ex);
            } else {
                log.warn("Publish outbox event id={} that bai, se retry (lan {})", event.getId(), event.getRetryCount(), ex);
            }
        }

        outboxEventRepository.save(event);
    }
}
