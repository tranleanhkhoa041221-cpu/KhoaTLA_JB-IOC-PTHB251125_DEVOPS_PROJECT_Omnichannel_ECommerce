package com.quickbite.edu.mapper;

import com.quickbite.edu.dto.response.PaymentResponse;
import com.quickbite.edu.dto.response.TransactionLogResponse;
import com.quickbite.edu.entity.Payment;
import com.quickbite.edu.entity.PaymentTransactionLog;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .orderCode(payment.getOrderCode())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod().name())
                .status(payment.getStatus().name())
                .vnpTxnRef(payment.getVnpTxnRef())
                .vnpTransactionNo(payment.getVnpTransactionNo())
                .bankCode(payment.getBankCode())
                .cardType(payment.getCardType())
                .paidAt(payment.getPaidAt())
                .failureReason(payment.getFailureReason())
                .refundedAmount(payment.getRefundedAmount())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    public TransactionLogResponse toLogResponse(PaymentTransactionLog log) {
        return TransactionLogResponse.builder()
                .id(log.getId())
                .paymentId(log.getPaymentId())
                .type(log.getType().name())
                .requestParams(log.getRequestParams())
                .responseParams(log.getResponseParams())
                .responseCode(log.getResponseCode())
                .message(log.getMessage())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
