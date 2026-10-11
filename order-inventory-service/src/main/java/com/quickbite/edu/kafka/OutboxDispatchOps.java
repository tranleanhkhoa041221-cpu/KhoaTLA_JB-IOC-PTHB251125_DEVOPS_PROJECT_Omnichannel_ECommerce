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
 * (Propagation.REQUIRES_NEW), tach rieng khoi OutboxEventPublisher (bean khac,
 * de @Transactional hoat dong dung qua Spring AOP Proxy - tranh loi self-invocation,
 * cung 1 quy uoc da dung o OrderPersistenceOps/InventoryStockOps).
 *
 * Vi sao tach moi event thanh 1 transaction rieng thay vi gop ca batch 50 event vao 1
 * transaction lon (cach lam CU):
 *  1. Row-lock (PESSIMISTIC_WRITE o findByIdForUpdate) chi bi giu trong thoi gian NGAN NHAT
 *     co the (1 event), khong chan cac instance khac xu ly cac event KHAC trong cung batch.
 *  2. Neu service crash giua batch (vd dang publish event thu 30/50), CHI co 1 event dang
 *     dang do bi anh huong - 29 event truoc do DA commit PUBLISHED that su, khong bi rollback
 *     va publish lai (giam duplicate so voi cach cu goi .get() tuan tu trong 1 transaction lon).
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
            // Instance khac da "claim" va xu ly xong event nay trong luc cho lock -> bo qua,
            // KHONG publish trung len Kafka.
            return;
        }

        try {
            // SUA LOI TICH HOP (thieu timeout): truoc day .get() KHONG co gioi han thoi gian ->
            // phu thuoc vao gia tri mac dinh cua Kafka client (delivery.timeout.ms=120s). Neu
            // Kafka Broker down/khong phan hoi, 1 thread trong dispatchExecutor (chi co 8 thread,
            // xem OutboxEventPublisher) se bi CHIEM DUNG toi 120s CHO 1 EVENT DUY NHAT, dong thoi
            // giu luon 1 row-lock PESSIMISTIC_WRITE suot thoi gian do - khong dong bo voi muc do
            // "fail-fast" ma phan con lai cua du an da ap dung nhat quan (gRPC deadline 3s o
            // ProductGrpcClient, Gateway httpclient response-timeout 8s). Dat gioi han ro rang 5s
            // (cung bac voi cac timeout khac trong du an) de giai phong thread/row-lock nhanh hon,
            // roi tang retryCount/chuyen FAILED (Outbox van dam bao KHONG MAT event - event se
            // duoc OutboxEventPublisher quet lai va thu dispatch tiep o tick sau).
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
