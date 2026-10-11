package com.quickbite.edu.repository;

import com.quickbite.edu.entity.OutboxEvent;
import com.quickbite.edu.entity.enums.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // Tang tu Top50 -> Top200 (Performance/Scalability) - xem giai thich chi tiet trong
    // OutboxEventRepository cua order-inventory-service (cung mau thiet ke, cung ly do).
    List<OutboxEvent> findTop200ByStatusOrderByIdAsc(OutboxStatus status);

    /**
     * SELECT ... FOR UPDATE tren DUNG 1 ban ghi - dung de "claim" (gianh quyen xu ly) 1 outbox
     * event truoc khi publish len Kafka, tranh 2 instance cua payment-billing-service cung publish
     * trung 1 event payment.completed/payment.failed khi chay nhieu instance song song
     * (horizontal scaling). Xem OutboxDispatchOps.dispatchOne() - cung mau thiet ke voi
     * order-inventory-service.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboxEvent o where o.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);
}
