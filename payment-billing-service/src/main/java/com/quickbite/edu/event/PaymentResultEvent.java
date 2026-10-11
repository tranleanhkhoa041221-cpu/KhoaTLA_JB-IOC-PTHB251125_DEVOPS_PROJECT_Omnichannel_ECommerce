package com.quickbite.edu.event;

import lombok.*;

import java.io.Serializable;

/**
 * Event Payment & Billing Service PHAT RA qua topic "payment.completed" hoac
 * "payment.failed" (Choreography Saga). Order & Inventory Service LANG NGHE
 * event nay de:
 *  - completed -> chuyen Order sang CONFIRMED (khoa/tru han ton kho da giu).
 *  - failed    -> compensating transaction: tra lai ton kho da giu + chuyen
 *                 Order sang CANCELLED.
 * CAU TRUC PHAI KHOP CHINH XAC voi order-inventory-service.event.PaymentResultEvent.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResultEvent implements Serializable {
    private Long orderId;
    private Long customerId;
    private String orderCode;
    private String transactionId;
    private String reason;
}
