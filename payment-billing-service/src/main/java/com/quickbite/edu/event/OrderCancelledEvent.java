package com.quickbite.edu.event;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event LANG NGHE tu topic "order.cancelled". Neu Payment tuong ung van con
 * PENDING (khach chua kip thanh toan) thi Payment Service se tu dong chuyen
 * Payment do sang CANCELLED - tranh tinh trang mo "giao dich ma" khong bao
 * gio duoc dong sau khi Order da bi huy.
 */
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
