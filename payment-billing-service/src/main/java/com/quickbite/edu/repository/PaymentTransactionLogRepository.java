package com.quickbite.edu.repository;

import com.quickbite.edu.entity.PaymentTransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentTransactionLogRepository extends JpaRepository<PaymentTransactionLog, Long> {

    List<PaymentTransactionLog> findByPaymentIdOrderByCreatedAtDesc(Long paymentId);
}
