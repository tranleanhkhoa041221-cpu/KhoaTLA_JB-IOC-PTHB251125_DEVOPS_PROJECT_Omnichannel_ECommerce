package com.quickbite.edu.entity.enums;

/**
 * Trang thai cua 1 ban ghi trong bang outbox_events (Outbox Pattern).
 * Dam bao viec cap nhat trang thai Payment va publish Kafka event la atomic
 * (khong bao gio mat event du Kafka broker co the tam thoi khong ket noi duoc).
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
