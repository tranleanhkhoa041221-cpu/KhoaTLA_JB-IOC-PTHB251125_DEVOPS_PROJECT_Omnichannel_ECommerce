package com.quickbite.edu.service.impl;

import com.quickbite.edu.dto.ProductStockInfo;
import com.quickbite.edu.dto.request.CancelOrderRequest;
import com.quickbite.edu.dto.request.CreateOrderRequest;
import com.quickbite.edu.dto.request.OrderItemRequest;
import com.quickbite.edu.dto.response.OrderResponse;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.entity.Order;
import com.quickbite.edu.entity.OrderItem;
import com.quickbite.edu.entity.OutboxEvent;
import com.quickbite.edu.entity.enums.OrderStatus;
import com.quickbite.edu.entity.enums.OutboxStatus;
import com.quickbite.edu.event.OrderCancelledEvent;
import com.quickbite.edu.event.OrderCreatedEvent;
import com.quickbite.edu.exception.BadRequestException;
import com.quickbite.edu.exception.ConflictException;
import com.quickbite.edu.exception.NotFoundException;
import com.quickbite.edu.grpc.AddressGrpcClient;
import com.quickbite.edu.grpc.ProductGrpcClient;
import com.quickbite.edu.kafka.KafkaTopics;
import com.quickbite.edu.mapper.OrderMapper;
import com.quickbite.edu.repository.OrderRepository;
import com.quickbite.edu.service.interfaces.InventoryService;
import com.quickbite.edu.service.interfaces.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final InventoryService inventoryService;
    private final ProductGrpcClient productGrpcClient;
    private final AddressGrpcClient addressGrpcClient;
    private final OrderPersistenceOps orderPersistenceOps;
    private final ObjectMapper objectMapper;

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        // Idempotency Check: neu client gui lai request voi cung idempotencyKey
        // (double-click, retry sau timeout) → tra ve don hang cu, KHONG tao moi.
        Optional<Order> existing = orderRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotency hit: idempotencyKey={} da ton tai, tra ve don hang cu orderCode={}",
                    request.getIdempotencyKey(), existing.get().getOrderCode());
            return orderMapper.toResponse(existing.get());
        }

        // Buoc 1: gRPC sang Identity/User Service de lay DIA CHI MAC DINH cua customer.
        // Order Service KHONG truy cap truc tiep database cua User Service.
        // Dia chi duoc snapshot vao orders.shipping_address de Order cu khong doi khi user sua/xoa address.
        String shippingAddress = addressGrpcClient.getDefaultShippingAddress(request.getCustomerId());

        // Buoc 2: gRPC sang Product Catalog — gia + ten SP + trang thai ACTIVE.
        // Ton kho that (reserve/commit) chi o buoc 3 (Inventory Service), tranh hai nguon ton kho.
        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .idempotencyKey(request.getIdempotencyKey())
                .customerId(request.getCustomerId())
                .shippingAddress(shippingAddress)
                .note(request.getNote())
                .status(OrderStatus.PENDING)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        Map<String, Integer> skuQuantities = new LinkedHashMap<>();

        for (OrderItemRequest itemReq : request.getItems()) {
            ProductStockInfo info = productGrpcClient.checkStockAndPrice(itemReq.getSku(), itemReq.getQuantity());
            if (!info.isAvailable()) {
                throw new BadRequestException(info.getMessage() != null
                        ? info.getMessage()
                        : "San pham SKU [" + itemReq.getSku() + "] khong kha dung");
            }
            BigDecimal subtotal = info.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            total = total.add(subtotal);

            order.addItem(OrderItem.builder()
                    .sku(itemReq.getSku())
                    .productName(info.getProductName())
                    .unitPrice(info.getPrice())
                    .quantity(itemReq.getQuantity())
                    .subtotal(subtotal)
                    .build());

            skuQuantities.merge(itemReq.getSku(), itemReq.getQuantity(), Integer::sum);
        }
        order.setTotalAmount(total);

        // Buoc 3: giu (reserve) ton kho noi bo - bao ve boi Redis Distributed Lock +
        // Pessimistic Locking trong PostgreSQL. Nem InsufficientStockException + tu
        // dong compensate (tra lai) neu 1 SKU bat ky khong du hang.
        inventoryService.reserveStock(skuQuantities);

        // Buoc 4: luu Order + phat OrderCreatedEvent qua Outbox Pattern (atomic,
        // cung 1 transaction DB - khong bao gio mat event du Kafka co the tam thoi down).
        try {
            // Payload outbox duoc tao SAU khi save (order.id da co) - sua loi OrderCreatedEvent.orderId = null.
            Order saved = orderPersistenceOps.saveNewOrderWithOutbox(order,
                    persisted -> buildOutboxEvent(persisted, OrderCreatedEvent.class.getSimpleName(),
                            KafkaTopics.ORDER_CREATED, orderMapper.toCreatedEvent(persisted)));
            log.info("Tao don hang thanh cong: orderCode={}, customerId={}", saved.getOrderCode(), saved.getCustomerId());
            return orderMapper.toResponse(saved);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Race hiem nhung co that: 2 request GIONG HET (cung idempotencyKey, vi du do client
            // retry sau timeout) cung vuot qua check findByIdempotencyKey() o dau method truoc khi
            // request nao kip luu DB (check-then-insert khong atomic). Request thua se va unique
            // constraint idempotency_key o day. Thay vi tra loi cho khach bang 1 loi 409 chung
            // chung (sai voi dung nghia cua idempotency - lan goi lai PHAI thay ket qua THANH CONG
            // cua lan truoc), ta truy lai don hang ma request kia vua tao va tra ve y het nhu cache-hit
            // o dau method - dung tinh chat idempotent.
            inventoryService.releaseStock(skuQuantities);
            Order winner = orderRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> ex);
            log.info("Phat hien race tren idempotencyKey={}, tra ve don hang do request khac vua tao: orderCode={}",
                    request.getIdempotencyKey(), winner.getOrderCode());
            return orderMapper.toResponse(winner);
        } catch (RuntimeException ex) {
            // Compensating transaction: neu luu DB that bai sau khi da giu kho -> tra lai kho ngay.
            log.error("Luu don hang that bai sau khi da giu kho, dang tra lai ton kho", ex);
            inventoryService.releaseStock(skuQuantities);
            throw ex;
        }
    }


    @Override
    public OrderResponse getById(Long id) {
        return orderMapper.toResponse(findOrderWithItems(id));
    }

    @Override
    public OrderResponse getByOrderCode(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new NotFoundException("Khong tim thay don hang [" + orderCode + "]"));
        return orderMapper.toResponse(order);
    }

    @Override
    public PageResponse<OrderResponse> search(Long customerId, OrderStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> result;
        if (customerId != null && status != null) {
            result = orderRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        } else if (customerId != null) {
            result = orderRepository.findByCustomerId(customerId, pageable);
        } else if (status != null) {
            result = orderRepository.findByStatus(status, pageable);
        } else {
            result = orderRepository.findAll(pageable);
        }

        return PageResponse.<OrderResponse>builder()
                .content(result.getContent().stream().map(orderMapper::toResponse).toList())
                .pageNumber(result.getNumber())
                .pageSize(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .isLast(result.isLast())
                .build();
    }

    @Override
    public OrderResponse cancelOrder(Long id, CancelOrderRequest request) {
        Order order = findOrderWithItems(id);
        assertCancellable(order);
        // Don CONFIRMED da commitStock (reservedQty da tru) -> chi duoc CONG LAI availableQty.
        // Don PENDING van con nam trong reservedQty -> release() (available += q, reserved -= q).
        final boolean wasConfirmed = order.getStatus() == OrderStatus.CONFIRMED;

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(request != null ? request.getReason() : "Khach hang huy don");

        OutboxEvent outboxEvent = buildOutboxEvent(order, OrderCancelledEvent.class.getSimpleName(),
                KafkaTopics.ORDER_CANCELLED, toCancelledEvent(order));

        Order saved;
        try {
            saved = orderPersistenceOps.updateStatusWithOutboxAndFlush(order, outboxEvent);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("Don hang dang duoc xu ly boi mot tien trinh khac, khong the huy.");
        }

        if (wasConfirmed) {
            inventoryService.returnConfirmedStock(toSkuQuantities(order));
        } else {
            releaseOrderInventory(order);
        }

        log.info("Da huy don hang orderCode={}, ly do={}", saved.getOrderCode(), saved.getCancelReason());
        return orderMapper.toResponse(saved);
    }

    @Override
    public void confirmOrder(Long orderId, String transactionId) {
        Order order = findOrderWithItems(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Bo qua confirmOrder cho orderId={} vi trang thai hien tai la {}", orderId, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.CONFIRMED);
        try {
            orderPersistenceOps.updateStatusAndFlush(order);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            log.warn("Order {} was already updated by another thread.", orderId);
            return;
        }

        // Thanh toan thanh cong -> "chot" so luong da giu, khong tra lai kho nua.
        inventoryService.commitStock(toSkuQuantities(order));

        log.info("Don hang orderCode={} da duoc xac nhan thanh toan (txn={})", order.getOrderCode(), transactionId);
    }

    @Override
    public void cancelOrderFromPaymentFailure(Long orderId, String reason) {
        Order order = findOrderWithItems(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Bo qua cancelOrderFromPaymentFailure cho orderId={} vi trang thai hien tai la {}", orderId, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason("Thanh toan that bai: " + reason);

        OutboxEvent outboxEvent = buildOutboxEvent(order, OrderCancelledEvent.class.getSimpleName(),
                KafkaTopics.ORDER_CANCELLED, toCancelledEvent(order));

        try {
            orderPersistenceOps.updateStatusWithOutboxAndFlush(order, outboxEvent);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            log.warn("Order {} was already updated.", orderId);
            return;
        }

        // Compensating Transaction (Saga): thanh toan that bai -> tra lai ton kho da giu.
        releaseOrderInventory(order);

        log.info("Da huy don hang orderCode={} do thanh toan that bai: {}", order.getOrderCode(), reason);
    }

    private void assertCancellable(Order order) {
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Khong the huy don hang o trang thai " + order.getStatus());
        }
    }

    private void releaseOrderInventory(Order order) {
        inventoryService.releaseStock(toSkuQuantities(order));
    }

    private Map<String, Integer> toSkuQuantities(Order order) {
        Map<String, Integer> skuQuantities = new LinkedHashMap<>();
        order.getItems().forEach(item -> skuQuantities.merge(item.getSku(), item.getQuantity(), Integer::sum));
        return skuQuantities;
    }

    private Order findOrderWithItems(Long id) {
        return orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new NotFoundException("Khong tim thay don hang co id=" + id));
    }

    private OrderCancelledEvent toCancelledEvent(Order order) {
        return OrderCancelledEvent.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .customerId(order.getCustomerId())
                .reason(order.getCancelReason())
                .cancelledAt(LocalDateTime.now())
                .build();
    }

    private OutboxEvent buildOutboxEvent(Order order, String eventType, String topic, Object payload) {
        return OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId(order.getId() != null ? String.valueOf(order.getId()) : "PENDING")
                .eventType(eventType)
                .topic(topic)
                .payload(objectMapper.writeValueAsString(payload))
                .status(OutboxStatus.PENDING)
                .build();
    }

    private String generateOrderCode() {
        return "ORD-" + LocalDateTime.now().toLocalDate().toString().replace("-", "")
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
