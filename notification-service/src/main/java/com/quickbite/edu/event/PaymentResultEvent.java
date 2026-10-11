package com.quickbite.edu.event;

import lombok.*;

import java.io.Serializable;

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
