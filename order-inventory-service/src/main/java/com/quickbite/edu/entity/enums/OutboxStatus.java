package com.quickbite.edu.entity.enums;

/**
 * Trang thai cua 1 ban ghi trong bang outbox_events (Outbox Pattern).
 * Dam bao viec ghi DB va publish Kafka event la atomic (khong mat event).
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
