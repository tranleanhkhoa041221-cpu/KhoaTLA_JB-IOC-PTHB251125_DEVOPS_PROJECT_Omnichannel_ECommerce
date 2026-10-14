package com.quickbite.edu.kafka;

/** Danh sach topic Kafka dung trong giao tiep bat dong bo (Event-Driven) giua cac service. */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    /** Payment Service LANG NGHE - Order & Inventory Service phat khi tao don + giu kho thanh cong. */
    public static final String ORDER_CREATED = "order.created";

    /** Payment Service LANG NGHE - Order & Inventory Service phat khi don bi huy. */
    public static final String ORDER_CANCELLED = "order.cancelled";

    /** Payment Service PHAT ra khi thanh toan thanh cong (Order Service lang nghe de CONFIRM don). */
    public static final String PAYMENT_COMPLETED = "payment.completed";

    /** Payment Service PHAT ra khi thanh toan that bai (Order Service lang nghe de compensate + huy don). */
    public static final String PAYMENT_FAILED = "payment.failed";

    /** Payment Service PHAT ra (qua Outbox) khi VNPay xac nhan hoan tien thanh cong (1 phan hoac toan bo). */
    public static final String PAYMENT_REFUNDED = "payment.refunded";
}
