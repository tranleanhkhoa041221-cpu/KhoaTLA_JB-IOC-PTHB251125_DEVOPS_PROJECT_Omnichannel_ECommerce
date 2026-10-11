package com.quickbite.edu.entity.enums;

public enum NotificationType {
    ORDER_CREATED,
    ORDER_CANCELLED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    /** Gui thu cong qua POST /api/notifications/test - phuc vu test tren Postman, khong qua Kafka. */
    MANUAL_TEST
}
