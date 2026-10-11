package com.quickbite.edu.vnpay;

import com.quickbite.edu.entity.Payment;

import java.math.BigDecimal;
import java.util.Map;

public interface VnPayService {

    /** Sinh URL thanh toan da ky (vnp_SecureHash) de redirect khach hang sang VNPay Sandbox. */
    String buildPaymentUrl(Payment payment, String clientIp);

    /** Kiem tra chu ky (vnp_SecureHash) cua params nhan tu VNPay (return URL hoac IPN). */
    boolean verifySignature(Map<String, String> params);

    /** Goi API querydr de doi soat trang thai 1 giao dich voi he thong VNPay. */
    Map<String, String> queryTransaction(Payment payment, String clientIp);

    /** Goi API refund de hoan tien mot phan/toan bo 1 giao dich da thanh toan thanh cong. */
    Map<String, String> refund(Payment payment, BigDecimal refundAmount, String reason, String createBy, String clientIp);
}
