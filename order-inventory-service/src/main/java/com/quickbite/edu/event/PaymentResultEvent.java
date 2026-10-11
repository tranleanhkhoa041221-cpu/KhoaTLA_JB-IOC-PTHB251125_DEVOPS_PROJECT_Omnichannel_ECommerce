package com.quickbite.edu.event;

import lombok.*;

import java.io.Serializable;

/**
 * Event Order Service LANG NGHE tu topic "payment.completed" / "payment.failed"
 * duoc phat boi Payment & Billing Service (Choreography Saga).
 * - completed -> chuyen Order sang CONFIRMED.
 * - failed    -> compensating transaction: tra lai ton kho da giu + chuyen Order sang CANCELLED.
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
