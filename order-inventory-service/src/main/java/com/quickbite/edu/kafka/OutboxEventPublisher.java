package com.quickbite.edu.kafka;

import com.quickbite.edu.entity.OutboxEvent;
import com.quickbite.edu.entity.enums.OutboxStatus;
import com.quickbite.edu.repository.OutboxEventRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Outbox Pattern - buoc 2: scheduled job quet cac ban ghi PENDING trong outbox_events va publish len Kafka.
 *
 * DAM BAO "KHONG MAT EVENT" KHI KAFKA DOWN (ke ca rat lau):
 *  - Event loi tam thoi LUON o lai PENDING (xem OutboxDispatchOps), khong bao gio tu chuyen FAILED.
 *  - CIRCUIT BREAKER + BACKOFF: khi 1 lan dispatch gap loi tam thoi, job tam dung theo backoff luy tien
 *    (1s, 2s, 4s ... toi da 30s). Khi het thoi gian cho, chi gui THAM DO 1 event; thanh cong thi dong
 *    breaker va xa het backlog. Nho vay luc Kafka down khong co 8 thread cung giu ket noi DB + row-lock
 *    de cho Kafka (lam can pool, anh huong tao don hang).
 *  - THU TU THEO AGGREGATE: cac event cung aggregateId (vd order.created roi order.cancelled cua cung 1 don)
 *    duoc gui TUAN TU theo id, dung lai o event loi dau tien; cac aggregate khac van chay SONG SONG.
 *  - Event hong vinh vien (payload rac...) bi chuyen FAILED ngay va khong chan cac event khac.
 *
 * An toan khi chay NHIEU INSTANCE: job chi SELECT danh sach ung vien; viec claim that su la row-lock
 * PESSIMISTIC_WRITE trong OutboxDispatchOps.dispatchOne().
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private static final int DISPATCH_CONCURRENCY = 8;
    private static final long BACKOFF_BASE_MS = 1_000L;
    private static final long BACKOFF_MAX_MS = 30_000L;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxDispatchOps outboxDispatchOps;
    private final ExecutorService dispatchExecutor = Executors.newFixedThreadPool(DISPATCH_CONCURRENCY);

    /** Thoi diem (epoch ms) cho toi khi breaker cho phep thu lai. */
    private volatile long pausedUntilMillis = 0L;
    /** So tick lien tiep gap loi tam thoi (0 = Kafka binh thuong). */
    private final AtomicInteger consecutiveFailedTicks = new AtomicInteger(0);
    private volatile boolean legacyRequeueDone = false;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        if (!legacyRequeueDone) {
            requeueLegacyFailedOnce();
        }
        if (System.currentTimeMillis() < pausedUntilMillis) {
            return; // breaker dang mo: cho het backoff
        }

        List<OutboxEvent> candidates = outboxEventRepository.findTop200ByStatusOrderByIdAsc(OutboxStatus.PENDING);
        if (candidates.isEmpty()) {
            return;
        }
        // Half-open: sau loi chi gui THAM DO 1 event de khong lam nghen DB khi Kafka van con down.
        if (consecutiveFailedTicks.get() > 0) {
            candidates = candidates.subList(0, 1);
        }

        Map<String, List<Long>> idsByAggregate = new LinkedHashMap<>();
        for (OutboxEvent candidate : candidates) {
            idsByAggregate.computeIfAbsent(candidate.getAggregateId(), k -> new ArrayList<>()).add(candidate.getId());
        }

        AtomicBoolean transientFailure = new AtomicBoolean(false);
        AtomicBoolean anyPublished = new AtomicBoolean(false);

        List<CompletableFuture<Void>> futures = idsByAggregate.values().stream()
                .map(ids -> CompletableFuture.runAsync(() -> dispatchGroup(ids, transientFailure, anyPublished), dispatchExecutor))
                .toList();

        // Cho tat ca nhom trong tick xong truoc khi tick ke tiep bat dau (tranh 2 tick chong lan).
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (transientFailure.get()) {
            int failedTicks = consecutiveFailedTicks.incrementAndGet();
            long backoff = Math.min(BACKOFF_MAX_MS, BACKOFF_BASE_MS << Math.min(failedTicks - 1, 5));
            pausedUntilMillis = System.currentTimeMillis() + backoff;
            log.warn("Outbox: Kafka loi tam thoi (lan {} lien tiep) - tam dung {} ms, cac event van o PENDING, se tu gui lai khi Kafka phuc hoi",
                    failedTicks, backoff);
        } else if (anyPublished.get() && consecutiveFailedTicks.getAndSet(0) > 0) {
            log.info("Outbox: Kafka da phuc hoi - tiep tuc gui backlog PENDING");
        }
    }

    private void dispatchGroup(List<Long> ids, AtomicBoolean transientFailure, AtomicBoolean anyPublished) {
        for (Long id : ids) {
            OutboxDispatchResult result;
            try {
                result = outboxDispatchOps.dispatchOne(id);
            } catch (Exception ex) {
                // Loi ha tang (DB, pool...) - coi nhu tam thoi, event van PENDING.
                log.warn("Outbox: dispatch event id={} gap loi ha tang: {}", id, ex.toString());
                transientFailure.set(true);
                return;
            }
            if (result == OutboxDispatchResult.TRANSIENT_FAILURE) {
                transientFailure.set(true);
                return; // giu thu tu: khong gui event sau cua cung aggregate
            }
            if (result == OutboxDispatchResult.PUBLISHED) {
                anyPublished.set(true);
            }
        }
    }

    private void requeueLegacyFailedOnce() {
        try {
            int n = outboxDispatchOps.requeueLegacyFailed();
            legacyRequeueDone = true;
            if (n > 0) {
                log.warn("Outbox: da dua {} event FAILED (do co che retry cu) ve PENDING de gui lai", n);
            }
        } catch (Exception ex) {
            log.warn("Outbox: chua requeue duoc event FAILED cu (se thu lai o tick sau): {}", ex.toString());
        }
    }

    @PreDestroy
    public void shutdown() {
        dispatchExecutor.shutdown();
        try {
            if (!dispatchExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                dispatchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dispatchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
