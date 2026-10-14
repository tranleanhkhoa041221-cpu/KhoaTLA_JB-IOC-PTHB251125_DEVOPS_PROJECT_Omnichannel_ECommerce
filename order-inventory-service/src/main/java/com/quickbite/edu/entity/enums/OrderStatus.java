package com.quickbite.edu.entity.enums;

/**
 * Vong doi trang thai cua mot don hang.
 * PENDING     -> vua tao, da giu (reserve) ton kho, cho thanh toan
 * CONFIRMED   -> thanh toan thanh cong (nhan event payment.completed)
 * SHIPPING    -> dang giao hang
 * COMPLETED   -> da giao thanh cong
 * CANCELLED   -> huy don (thanh toan that bai / khach huy / het ton kho)
 * PARTIALLY_REFUNDED -> da hoan 1 phan tien (nhan event payment.refunded, fullyRefunded=false)
 * REFUNDED    -> da hoan TOAN BO tien (nhan event payment.refunded, fullyRefunded=true)
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPING,
    COMPLETED,
    CANCELLED,
    PARTIALLY_REFUNDED,
    REFUNDED
}
