package com.quickbite.edu.kafka;

import com.quickbite.edu.entity.OutboxEvent;
import com.quickbite.edu.entity.enums.OutboxStatus;
import com.quickbite.edu.repository.OutboxEventRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Outbox Pattern - buoc 2: scheduled job quet cac ban ghi PENDING trong bang outbox_events
 * (da duoc ghi ATOMIC cung transaction voi Order o buoc 1) va giao tung event cho
 * OutboxDispatchOps xu ly rieng le (xem OutboxDispatchOps de biet ly do tach transaction).
 *
 * An toan khi chay NHIEU INSTANCE song song (horizontal scaling): method nay chi SELECT thong
 * thuong (khong lock) de lay danh sach UNG VIEN, viec "claim" that su (tranh 2 instance cung
 * publish 1 event) dien ra o buoc sau trong OutboxDispatchOps.dispatchOne() bang row-lock
 * PESSIMISTIC_WRITE. Neu Kafka Broker down, event van nam trong DB (status=PENDING) va se
 * duoc thu lai o lan quet tiep theo - KHONG BAO GIO MAT EVENT.
 *
 * PERFORMANCE/SCALABILITY (sua ngay 2026-10): truoc day moi tick CHI xu ly 50 event mot cach
 * TUAN TU (for loop goi dispatchOne() lan luot, moi lan cho .get() cua Kafka send xong moi sang
 * event ke tiep) - gioi han cung throughput toi da cua toan bo outbox publisher o muc ~50
 * event/giay, THAP HON NHIEU so voi muc tieu Flash Sale 500-1000 req/s cua de bai, khien
 * outbox_events bi don (backlog) tang khong gioi han trong luc tai cao. Sua bang 2 thay doi:
 *  1) Tang gioi han lay ung vien moi tick tu Top50 -> Top200 (OutboxEventRepository).
 *  2) Dispatch SONG SONG nhieu event trong CUNG 1 tick qua 1 ExecutorService co gioi han
 *     (bounded) thay vi vong for tuan tu - an toan vi dispatchOne() da la 1 transaction
 *     REQUIRES_NEW doc lap + row-lock PESSIMISTIC_WRITE cho tung event rieng biet (khong
 *     event nao tranh chap lock voi event khac), nen co the chay dong thoi ma khong vi pham
 *     tinh dung dan cua Outbox/idempotency.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    /** Gioi han so thread dispatch dong thoi - can bang voi so ket noi DB con lai trong
     *  HikariCP pool (xem application.yml) de tranh outbox publisher chiem het pool khien
     *  cac request nghiep vu khac (tao don hang) bi cho connection. */
    private static final int DISPATCH_CONCURRENCY = 8;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxDispatchOps outboxDispatchOps;
    private final ExecutorService dispatchExecutor = Executors.newFixedThreadPool(DISPATCH_CONCURRENCY);

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        List<OutboxEvent> candidates = outboxEventRepository.findTop200ByStatusOrderByIdAsc(OutboxStatus.PENDING);
        if (candidates.isEmpty()) {
            return;
        }

        List<CompletableFuture<Void>> futures = candidates.stream()
                .map(candidate -> CompletableFuture.runAsync(
                        () -> outboxDispatchOps.dispatchOne(candidate.getId()), dispatchExecutor))
                .toList();

        // Cho tat ca event trong tick nay xu ly xong truoc khi @Scheduled tick ke tiep bat dau,
        // tranh 2 tick chong lan nhau cung xu ly 1 batch.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
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
