package com.quickbite.edu.event;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Phat ra khi don hang bi huy, de Notification Service bao cho khach hang biet. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCancelledEvent implements Serializable {
    private Long orderId;
    private String orderCode;
    private Long customerId;
    private String reason;
    private LocalDateTime cancelledAt;
}
