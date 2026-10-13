package com.quickbite.edu.repository;

import com.quickbite.edu.entity.Order;
import com.quickbite.edu.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    Optional<Order> findByOrderCode(String orderCode);

    /** Idempotency check — tra ve don hang neu key da ton tai */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByCustomerIdAndStatus(Long customerId, OrderStatus status, Pageable pageable);

    /**
     * Tim cac don PENDING da ton tai qua ngưỡng thoi gian (dung cho Saga Compensation Cron).
     * Don PENDING qua lau = thanh toan chua hoan tat hoac service crash giua chung.
     */
    // LEFT JOIN FETCH o.items: SagaCompensationJob chay NGOAI transaction/session, neu items (LAZY) khong duoc
    // nap san thi order.getItems() nem LazyInitializationException SAU KHI don da bi huy -> khong tra duoc kho.
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.status = 'PENDING' AND o.createdAt < :threshold")
    List<Order> findStalePendingOrders(@Param("threshold") LocalDateTime threshold);
}