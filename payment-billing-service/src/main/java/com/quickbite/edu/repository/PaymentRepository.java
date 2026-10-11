package com.quickbite.edu.repository;

import com.quickbite.edu.entity.Payment;
import com.quickbite.edu.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByVnpTxnRef(String vnpTxnRef);

    boolean existsByOrderId(Long orderId);

    Page<Payment> findByCustomerId(Long customerId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Page<Payment> findByCustomerIdAndStatus(Long customerId, PaymentStatus status, Pageable pageable);
}
