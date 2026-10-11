package com.quickbite.edu.kafka;

/** Danh sach topic Kafka dung trong giao tiep bat dong bo (Event-Driven) giua cac service. */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    /** Order Service PHAT ra sau khi tao don + giu kho thanh cong. */
    public static final String ORDER_CREATED = "order.created";

    /** Order Service PHAT ra khi don bi huy (chu dong huy hoac do payment that bai). */
    public static final String ORDER_CANCELLED = "order.cancelled";

    /** Order Service LANG NGHE - Payment Service phat khi thanh toan thanh cong. */
    public static final String PAYMENT_COMPLETED = "payment.completed";

    /** Order Service LANG NGHE - Payment Service phat khi thanh toan that bai (can compensate). */
    public static final String PAYMENT_FAILED = "payment.failed";
}
