package com.quickbite.edu.kafka;

/**
 * Ket qua dispatch 1 OutboxEvent (xem OutboxDispatchOps / OutboxEventPublisher).
 */
public enum OutboxDispatchResult {
    /** Da publish len Kafka, event chuyen PUBLISHED. */
    PUBLISHED,
    /** Event khong con PENDING (instance khac da xu ly) hoac khong ton tai - khong lam gi. */
    SKIPPED,
    /** Loi TAM THOI (Kafka down/cham, mang, timeout...). Event van PENDING, se thu lai voi backoff. */
    TRANSIENT_FAILURE,
    /** Loi VINH VIEN (payload khong phai JSON, record qua lon, topic sai...). Event chuyen FAILED, can xu ly tay. */
    PERMANENT_FAILURE
}
