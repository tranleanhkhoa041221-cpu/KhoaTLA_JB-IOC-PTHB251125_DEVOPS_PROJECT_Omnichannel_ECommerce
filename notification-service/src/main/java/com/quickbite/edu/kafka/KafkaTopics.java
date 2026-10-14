package com.quickbite.edu.kafka;

/** Topic Kafka dung chung voi Order & Inventory / Payment & Billing Service. */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";

    /** Notification LANG NGHE - Payment Service phat khi hoan tien thanh cong -> bao khach. */
    public static final String PAYMENT_REFUNDED = "payment.refunded";
}
