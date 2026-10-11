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
 * (da duoc ghi ATOMIC cung transaction voi Payment o buoc 1 trong PaymentPersistenceOps) va
 * giao tung event cho OutboxDispatchOps xu ly rieng le. An toan khi chay nhieu instance song
 * song - xem OutboxDispatchOps.
 *
 * PERFORMANCE/SCALABILITY (sua ngay 2026-10): dispatch SONG SONG nhieu event/tick qua 1
 * ExecutorService gioi han thay vi vong for tuan tu, va tang gioi han lay ung vien Top50 ->
 * Top200/tick - xem giai thich chi tiet trong OutboxEventPublisher cua order-inventory-service
 * (cung ly do: throughput cu ~50 event/s thap hon nhieu so voi muc tieu Flash Sale 500-1000
 * req/s, gay backlog outbox_events khong gioi han).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

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
