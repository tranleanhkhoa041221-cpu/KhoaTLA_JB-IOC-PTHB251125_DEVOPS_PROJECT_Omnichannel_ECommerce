package com.quickbite.edu.entity;

import com.quickbite.edu.entity.enums.PaymentMethod;
import com.quickbite.edu.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Giao dich thanh toan ung voi 1 Order ben Order & Inventory Service.
 * Duoc tao TU DONG (trang thai PENDING) ngay khi Payment Service nhan duoc
 * OrderCreatedEvent qua Kafka - dung thiet ke "Payment Service lang nghe
 * event de khoi tao giao dich thanh toan" theo dung de bai.
 *
 * Luu y (Database Per Service Pattern): bang nay KHONG tham chieu FK truc
 * tiep sang bang orders cua Order Service (khac database) - orderId/orderCode
 * chi la snapshot du lieu nhan duoc tu event.
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_order_id", columnList = "order_id", unique = true),
        @Index(name = "idx_payments_customer_id", columnList = "customer_id"),
        @Index(name = "idx_payments_status", columnList = "status"),
        @Index(name = "idx_payments_vnp_txn_ref", columnList = "vnp_txn_ref", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "order_code", nullable = false, length = 40)
    private String orderCode;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.VNPAY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Ma tham chieu DUY NHAT gui sang VNPay (vnp_TxnRef) - sinh tu orderCode + timestamp. */
    @Column(name = "vnp_txn_ref", nullable = false, unique = true, length = 50)
    private String vnpTxnRef;

    /**
     * Luu lai CHINH XAC gia tri vnp_CreateDate (yyyyMMddHHmmss) da gui luc tao URL
     * thanh toan. Bat buoc phai gui lai dung gia tri nay khi goi API querydr/refund
     * (VNPay doi hoi vnp_TransactionDate khop voi vnp_CreateDate ban dau).
     */
    @Column(name = "vnp_create_date", length = 20)
    private String vnpCreateDate;

    /** Ma giao dich phia VNPay tra ve sau khi thanh toan thanh cong (vnp_TransactionNo). */
    @Column(name = "vnp_transaction_no", length = 50)
    private String vnpTransactionNo;

    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @Column(name = "card_type", length = 20)
    private String cardType;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** So tien da hoan (cong don qua cac lan refund mot phan). */
    @Column(name = "refunded_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isPending() {
        return this.status == PaymentStatus.PENDING;
    }

    public boolean isSuccess() {
        return this.status == PaymentStatus.SUCCESS;
    }
}
