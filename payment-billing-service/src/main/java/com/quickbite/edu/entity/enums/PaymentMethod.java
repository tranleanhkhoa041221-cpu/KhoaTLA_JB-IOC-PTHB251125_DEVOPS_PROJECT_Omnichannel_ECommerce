package com.quickbite.edu.entity.enums;

/**
 * Cac phuong thuc thanh toan he thong ho tro.
 * Hien tai chi trien khai VNPAY (theo huong dan cua de bai), thiet ke du
 * cho phep mo rong them MOMO / ZALOPAY / STRIPE / COD sau nay ma khong
 * pha vo hop dong du lieu hien co (Payment.paymentMethod).
 */
public enum PaymentMethod {
    VNPAY,
    COD
}
