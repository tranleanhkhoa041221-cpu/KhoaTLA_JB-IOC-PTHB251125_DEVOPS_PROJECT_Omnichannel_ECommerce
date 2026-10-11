package com.quickbite.edu.controller;

import com.quickbite.edu.dto.request.RefundPaymentRequest;
import com.quickbite.edu.dto.response.ApiResponse;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.dto.response.PaymentResponse;
import com.quickbite.edu.dto.response.PaymentUrlResponse;
import com.quickbite.edu.dto.response.TransactionLogResponse;
import com.quickbite.edu.entity.enums.PaymentStatus;
import com.quickbite.edu.exception.ForbiddenException;
import com.quickbite.edu.service.interfaces.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ownership (CUSTOMER chi xem/thanh toan giao dich CUA CHINH MINH) duoc kiem tra THEM o day,
 * ngoai SecurityConfig (tuong tu OrderController ben order-inventory-service) - vi Spring
 * Security khong the bieu dien dieu kien "customerId == id trong JWT" bang URL pattern.
 * /api/payments/vnpay/** (VnPayCallbackController) KHONG nam trong controller nay va la route
 * PUBLIC rieng, khong bi anh huong boi cac kiem tra ben duoi.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Sinh URL redirect sang VNPay Sandbox cho don hang {orderId}.
     * Payment tuong ung phai da duoc Payment Service tu tao san (trang thai PENDING)
     * ngay khi nhan event order.created qua Kafka - endpoint nay chi "kich hoat"
     * buoc redirect khach hang sang cong thanh toan.
     */
    @PostMapping("/order/{orderId}/vnpay-url")
    public ResponseEntity<ApiResponse<PaymentUrlResponse>> createVnPayUrl(
            @PathVariable Long orderId,
            HttpServletRequest httpRequest,
            Authentication authentication
    ) {
        assertOwnerOrAdmin(paymentService.getByOrderId(orderId).getCustomerId(), authentication);
        PaymentUrlResponse response = paymentService.createPaymentUrl(orderId, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Da tao URL thanh toan VNPay", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getById(@PathVariable Long id, Authentication authentication) {
        PaymentResponse response = paymentService.getById(id);
        assertOwnerOrAdmin(response.getCustomerId(), authentication);
        return ResponseEntity.ok(ApiResponse.success("Lay giao dich thanh cong", response));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByOrderId(@PathVariable Long orderId,
                                                                      Authentication authentication) {
        PaymentResponse response = paymentService.getByOrderId(orderId);
        assertOwnerOrAdmin(response.getCustomerId(), authentication);
        return ResponseEntity.ok(ApiResponse.success("Lay giao dich thanh cong", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> search(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        Long effectiveCustomerId = isAdmin(authentication) ? customerId : currentUserId(authentication);
        PageResponse<PaymentResponse> response = paymentService.search(effectiveCustomerId, status, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lay danh sach giao dich thanh cong", response));
    }

    /** Doi soat tai chinh: chu dong hoi VNPay trang thai that su cua giao dich (API querydr). */
    @GetMapping("/{id}/query")
    public ResponseEntity<ApiResponse<PaymentResponse>> queryTransaction(
            @PathVariable Long id,
            HttpServletRequest httpRequest,
            Authentication authentication
    ) {
        assertOwnerOrAdmin(paymentService.getById(id).getCustomerId(), authentication);
        PaymentResponse response = paymentService.queryTransaction(id, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Doi soat giao dich voi VNPay thanh cong", response));
    }

    /**
     * Hoan tien mot phan/toan bo 1 giao dich da thanh toan thanh cong (API refund).
     * CHI ADMIN - da duoc chan o SecurityConfig (/api/payments/*{@code /refund} -> hasRole(ADMIN)),
     * khong can kiem tra ownership vi ADMIN duoc phep hoan tien cho BAT KY khach hang nao.
     */
    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(
            @PathVariable Long id,
            @RequestBody @Valid RefundPaymentRequest request,
            HttpServletRequest httpRequest
    ) {
        PaymentResponse response = paymentService.refund(id, request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Hoan tien thanh cong", response));
    }

    /** Lich su tuong tac voi VNPay cua 1 giao dich (tao URL / return / IPN / query / refund) - phuc vu doi soat. */
    @GetMapping("/{id}/logs")
    public ResponseEntity<ApiResponse<List<TransactionLogResponse>>> getLogs(@PathVariable Long id,
                                                                              Authentication authentication) {
        assertOwnerOrAdmin(paymentService.getById(id).getCustomerId(), authentication);
        return ResponseEntity.ok(ApiResponse.success("Lay lich su giao dich thanh cong", paymentService.getLogs(id)));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    private void assertOwnerOrAdmin(Long resourceCustomerId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }
        if (!currentUserId(authentication).equals(resourceCustomerId)) {
            throw new ForbiddenException("Ban khong co quyen truy cap giao dich cua khach hang khac");
        }
    }
}
