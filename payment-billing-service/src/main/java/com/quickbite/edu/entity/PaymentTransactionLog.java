package com.quickbite.edu.entity;

import com.quickbite.edu.entity.enums.TransactionLogType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Nhat ky RAW tung lan trao doi voi VNPay (tao URL / return / IPN / query / refund).
 * VNPay da luu lich su giao dich tren nen tang cua ho (giong nhu 1 dich vu ben
 * thu 3 kieu email/cloud) - viec cua Payment Service la GHI LAI ve DB cua minh
 * de tra cuu/doi soat noi bo ma khong phu thuoc vao he thong cua VNPay.
 */
@Entity
@Table(name = "payment_transaction_logs", indexes = {
        @Index(name = "idx_txn_logs_payment_id", columnList = "payment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransactionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Co the NULL trong truong hop VNPay goi callback (return/IPN) voi
     * vnp_TxnRef khong khop bat ky giao dich nao trong he thong - van can
     * ghi log lai de dieu tra du khong gan duoc voi Payment cu the nao.
     */
    @Column(name = "payment_id")
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionLogType type;

    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams;

    @Column(name = "response_params", columnDefinition = "TEXT")
    private String responseParams;

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Column(length = 500)
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
