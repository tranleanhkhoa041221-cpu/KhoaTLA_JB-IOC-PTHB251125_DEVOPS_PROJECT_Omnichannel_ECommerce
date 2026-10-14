package com.quickbite.edu.event;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Event "payment.refunded" - Payment & Billing Service PHAT RA (qua Outbox) moi khi VNPay xac nhan
 * hoan tien thanh cong (hoan 1 phan HOAC toan bo).
 *  - Order & Inventory Service nghe de doi trang thai don: PARTIALLY_REFUNDED / REFUNDED.
 *    KHONG tu dong cong lai kho (hoan tien != hang da ve kho; kho chi cong khi hang that su tra ve - restock thu cong).
 *  - Notification Service nghe de bao khach da duoc hoan tien.
 * CAU TRUC PHAI KHOP voi class cung ten o order-inventory-service va notification-service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRefundedEvent implements Serializable {
    private Long orderId;
    private Long customerId;
    private String orderCode;
    private String transactionId;
    /** So tien cua LAN hoan nay. */
    private BigDecimal refundAmount;
    /** Tong so tien da hoan (cong don) sau lan hoan nay. */
    private BigDecimal totalRefundedAmount;
    /** So tien thanh toan goc cua payment. */
    private BigDecimal paymentAmount;
    /** true khi tong da hoan >= so tien goc (don chuyen REFUNDED); false = hoan 1 phan. */
    private boolean fullyRefunded;
    private String reason;
}
