package com.quickbite.edu.entity.enums;

/**
 * Loai su kien duoc ghi vao payment_transaction_logs - luu lai TOAN BO lich su
 * trao doi voi cong thanh toan VNPay (request/response tho) de phuc vu doi soat
 * tai chinh sau nay, kem theo lich su ma VNPay da luu tren nen tang cua ho.
 */
public enum TransactionLogType {
    /** Server sinh URL thanh toan va redirect khach sang VNPay. */
    CREATE_PAYMENT_URL,
    /** Khach duoc VNPay redirect tro ve returnUrl sau khi thanh toan (khong dang tin cay 100%). */
    RETURN_URL,
    /** VNPay goi server-to-server (webhook) bao ket qua giao dich - nguon du lieu dang tin cay nhat. */
    IPN_CALLBACK,
    /** Merchant chu dong goi API querydr de doi soat trang thai giao dich voi VNPay. */
    QUERY_TRANSACTION,
    /** Merchant goi API refund de hoan tien mot phan/toan bo giao dich. */
    REFUND
}
