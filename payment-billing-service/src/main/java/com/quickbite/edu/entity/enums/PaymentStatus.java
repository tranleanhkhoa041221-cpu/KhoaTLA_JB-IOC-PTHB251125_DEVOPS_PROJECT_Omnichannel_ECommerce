package com.quickbite.edu.entity.enums;

/**
 * Vong doi trang thai cua mot giao dich thanh toan (Payment).
 * PENDING     -> vua duoc tao khi nhan OrderCreatedEvent, cho khach bam thanh toan
 * PROCESSING  -> khach da duoc redirect sang VNPay, dang cho ket qua (return/IPN)
 * SUCCESS     -> VNPay xac nhan thanh toan thanh cong (vnp_ResponseCode = 00)
 * FAILED      -> VNPay tra ve loi, het han, hoac sai chu ky
 * CANCELLED   -> don hang goc bi huy truoc khi thanh toan xong (PENDING -> CANCELLED)
 * REFUNDED    -> giao dich SUCCESS da duoc hoan tien (mot phan hoac toan bo)
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED,
    REFUNDED
}
