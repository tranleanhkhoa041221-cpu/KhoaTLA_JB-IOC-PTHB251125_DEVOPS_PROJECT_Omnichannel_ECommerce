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

/**
 * Xu ly publish 1 OutboxEvent DUY NHAT trong 1 transaction NGAN va DOC LAP (REQUIRES_NEW),
 * tach rieng khoi OutboxEventPublisher (bean khac, de @Transactional hoat dong qua Spring AOP proxy).
 *
 * CHINH SACH LOI (sua de KHONG BAO GIO MAT EVENT khi Kafka down lau):
 *  - Loi TAM THOI (Kafka down/cham, timeout, mang...): event GIU NGUYEN trang thai PENDING, chi tang
 *    retryCount de theo doi. Khong co nguong "het so lan thu thi chuyen FAILED" nua - truoc day
 *    MAX_RETRY=5 lam event chuyen FAILED sau vai phut Kafka down, OutboxEventPublisher chi quet
 *    PENDING nen event bi MAT vinh vien khi Kafka len lai. Viec cho/backoff do OutboxEventPublisher dam nhiem.
 *  - Loi VINH VIEN (payload khong phai JSON, record qua lon, serialization, topic khong hop le):
 *    chuyen FAILED ngay (thu lai vo ich, va khong de 1 event "doc" chan cac event khac).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatchOps {

    /** Nguong retryCount cua cac event FAILED DO CO CHE CU (MAX_RETRY=5): coi la loi tam thoi, dua ve PENDING 1 lan luc khoi dong. */
    static final int LEGACY_MAX_RETRY = 5;

    /** Phai LON HON delivery.timeout.ms (10s, xem application.yml) de future luon hoan tat (thanh cong/that bai) truoc khi get() het han -> tranh gui trung. */
    private static final long SEND_TIMEOUT_SECONDS = 15;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutboxDispatchResult dispatchOne(Long eventId) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.PENDING) {
            // Instance khac da claim va xu ly xong event nay -> bo qua, KHONG publish trung.
            return OutboxDispatchResult.SKIPPED;
        }

        // Chan payload rac (vi du ma OID cua Large Object cu nhu "16718"): gui len Kafka chi tao message
        // doc lam consumer day sang DLT. Danh dau FAILED de nguoi van hanh xu ly.
        if (!looksLikeJson(event.getPayload())) {
            event.setStatus(OutboxStatus.FAILED);
            outboxEventRepository.save(event);
            log.error("Outbox event id={} type={} co payload KHONG phai JSON - chuyen FAILED, khong publish (can kiem tra thu cong)",
                    event.getId(), event.getEventType());
            return OutboxDispatchResult.PERMANENT_FAILURE;
        }

        try {
            kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
            log.info("Da publish outbox event id={} type={} topic={}", event.getId(), event.getEventType(), event.getTopic());
            return OutboxDispatchResult.PUBLISHED;
        } catch (InterruptedException ex) {
            // Service dang tat: giu nguyen PENDING (khong tang retry), lan khoi dong sau se gui tiep.
            Thread.currentThread().interrupt();
            return OutboxDispatchResult.TRANSIENT_FAILURE;
        } catch (Exception ex) {
            if (isPermanent(ex)) {
                event.setStatus(OutboxStatus.FAILED);
                outboxEventRepository.save(event);
                log.error("Outbox event id={} type={} loi VINH VIEN ({}) - chuyen FAILED, can kiem tra thu cong",
                        event.getId(), event.getEventType(), rootDescription(ex));
                return OutboxDispatchResult.PERMANENT_FAILURE;
            }
            event.setRetryCount(event.getRetryCount() + 1);
            outboxEventRepository.save(event);
            log.warn("Publish outbox event id={} that bai tam thoi (lan {}): {} - giu PENDING, se thu lai voi backoff",
                    event.getId(), event.getRetryCount(), rootDescription(ex));
            return OutboxDispatchResult.TRANSIENT_FAILURE;
        }
    }

    /**
     * Chay 1 lan sau khi khoi dong: dua cac event FAILED do co che cu (retry_count >= 5, tuc loi tam thoi
     * khi Kafka tung down) ve PENDING de duoc gui lai. Event FAILED do loi vinh vien se tu bi danh dau lai FAILED
     * ngay o lan dispatch dau tien.
     */
    @Transactional
    public int requeueLegacyFailed() {
        return outboxEventRepository.requeueFailedWithRetryAtLeast(LEGACY_MAX_RETRY);
    }

    private static boolean looksLikeJson(String payload) {
        if (payload == null) {
            return false;
        }
        String p = payload.strip();
        return p.startsWith("{") || p.startsWith("[");
    }

    /** Khong import truc tiep class cua kafka-clients: nhan dien theo ten class trong chuoi cause. */
    private static boolean isPermanent(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String n = t.getClass().getSimpleName();
            if (n.equals("RecordTooLargeException") || n.equals("SerializationException") || n.equals("InvalidTopicException")) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static String rootDescription(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
