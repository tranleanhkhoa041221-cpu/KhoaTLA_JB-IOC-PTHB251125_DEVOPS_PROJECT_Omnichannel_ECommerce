package com.quickbite.edu.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private String orderCode;
    private Long customerId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String status;
    private String vnpTxnRef;
    private String vnpTransactionNo;
    private String bankCode;
    private String cardType;
    private LocalDateTime paidAt;
    private String failureReason;
    private BigDecimal refundedAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
