package com.quickbite.edu.controller;

import com.quickbite.edu.dto.request.CancelOrderRequest;
import com.quickbite.edu.dto.request.CreateOrderRequest;
import com.quickbite.edu.dto.response.ApiResponse;
import com.quickbite.edu.dto.response.OrderResponse;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.entity.enums.OrderStatus;
import com.quickbite.edu.exception.ForbiddenException;
import com.quickbite.edu.service.interfaces.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * BAO MAT theo cap do tai nguyen (ownership) KHONG the khai bao bang URL pattern trong
 * SecurityConfig (Spring Security chi biet "co quyen ROLE_X vao /api/orders/**", khong biet
 * "customerId cua order #5 co phai la chinh nguoi dang goi khong") - nen duoc kiem tra THEM
 * ngay tai Controller bang 2 ham tien ich isAdmin()/isOwner() ben duoi, AP DUNG CHO MOI
 * endpoint dung/sua du lieu don hang:
 *  - POST   /api/orders            : customerId luon bi GHI DE bang chinh ID cua nguoi dang
 *    nhap (tru ADMIN), KHONG BAO GIO tin tuong customerId client tu gui len trong request body
 *    (IDOR - khach A khong the "dat ho" don hang dung ten khach B).
 *  - GET    /api/orders/{id}, /code/{orderCode}, PUT /{id}/cancel : CUSTOMER chi xem/huy duoc
 *    don hang CUA CHINH MINH, khac id bi tra ve 403 (ADMIN duoc xem/huy tat ca).
 *  - GET    /api/orders (search)   : CUSTOMER bi EP loc theo dung customerId cua minh (tham so
 *    customerId client truyen len bi bo qua), ADMIN duoc loc theo bat ky customerId nao.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@RequestBody @Valid CreateOrderRequest request,
                                                               Authentication authentication) {
        if (!isAdmin(authentication)) {
            request.setCustomerId(currentUserId(authentication));
        }
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Dat hang thanh cong", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable Long id, Authentication authentication) {
        OrderResponse response = orderService.getById(id);
        assertOwnerOrAdmin(response.getCustomerId(), authentication);
        return ResponseEntity.ok(ApiResponse.success("Lay don hang thanh cong", response));
    }

    @GetMapping("/code/{orderCode}")
    public ResponseEntity<ApiResponse<OrderResponse>> getByOrderCode(@PathVariable String orderCode,
                                                                      Authentication authentication) {
        OrderResponse response = orderService.getByOrderCode(orderCode);
        assertOwnerOrAdmin(response.getCustomerId(), authentication);
        return ResponseEntity.ok(ApiResponse.success("Lay don hang thanh cong", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> search(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        Long effectiveCustomerId = isAdmin(authentication) ? customerId : currentUserId(authentication);
        PageResponse<OrderResponse> response = orderService.search(effectiveCustomerId, status, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lay danh sach don hang thanh cong", response));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) CancelOrderRequest request,
            Authentication authentication
    ) {
        OrderResponse existing = orderService.getById(id);
        assertOwnerOrAdmin(existing.getCustomerId(), authentication);
        return ResponseEntity.ok(ApiResponse.success("Huy don hang thanh cong", orderService.cancelOrder(id, request)));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    private void assertOwnerOrAdmin(Long resourceCustomerId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }
        if (!currentUserId(authentication).equals(resourceCustomerId)) {
            throw new ForbiddenException("Ban khong co quyen truy cap don hang cua khach hang khac");
        }
    }
}
