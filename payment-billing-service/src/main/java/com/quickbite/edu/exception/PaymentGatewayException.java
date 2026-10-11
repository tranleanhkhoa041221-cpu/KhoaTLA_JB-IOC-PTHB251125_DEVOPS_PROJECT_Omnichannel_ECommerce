package com.quickbite.edu.exception;

/**
 * Nem ra khi giao tiep voi VNPay that bai: sai chu ky (secure hash), VNPay
 * tra ve ma loi khi goi API refund/querydr, hoac response khong hop le.
 */
public class PaymentGatewayException extends ConflictException {
    public PaymentGatewayException(String message) {
        super(message);
    }
}
