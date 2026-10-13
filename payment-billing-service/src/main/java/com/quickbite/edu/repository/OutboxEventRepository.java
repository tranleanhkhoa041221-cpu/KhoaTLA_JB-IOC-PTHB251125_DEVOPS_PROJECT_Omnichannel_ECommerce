package com.quickbite.edu.repository;

import com.quickbite.edu.entity.OutboxEvent;
import com.quickbite.edu.entity.enums.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Top 200 event PENDING cu nhat moi tick (xem OutboxEventPublisher). */
    List<OutboxEvent> findTop200ByStatusOrderByIdAsc(OutboxStatus status);

    /** Row-lock 1 event de claim truoc khi publish, tranh nhieu instance gui trung (xem OutboxDispatchOps). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboxEvent o where o.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);

    /** Dua event FAILED do co che retry cu (retry_count >= nguong) ve PENDING de gui lai. */
    @Modifying(clearAutomatically = true)
    @Query("update OutboxEvent o set o.status = com.quickbite.edu.entity.enums.OutboxStatus.PENDING, o.retryCount = 0 "
            + "where o.status = com.quickbite.edu.entity.enums.OutboxStatus.FAILED and o.retryCount >= :minRetry")
    int requeueFailedWithRetryAtLeast(@Param("minRetry") int minRetry);
}
