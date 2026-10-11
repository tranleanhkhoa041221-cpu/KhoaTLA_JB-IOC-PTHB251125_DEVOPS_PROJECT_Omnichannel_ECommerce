package com.quickbite.edu.controller;

import com.quickbite.edu.dto.request.ProductRequest;
import com.quickbite.edu.dto.response.ApiResponse;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.dto.response.ProductResponse;
import com.quickbite.edu.dto.response.ProductVariantResponse;
import com.quickbite.edu.entity.enums.ProductStatus;
import com.quickbite.edu.service.interfaces.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Data Ownership (IDOR fix): giong het mau da dung o OrderController (order-inventory-service)/
 * PaymentController (payment-billing-service) - Spring Security (SecurityConfig) chi biet
 * "co ROLE_SELLER thi duoc goi POST/PUT/DELETE /api/catalog/products/**", KHONG biet "san pham
 * #5 co phai do CHINH SELLER nay tao khong". Ownership (sellerId == currentUserId) duoc kiem
 * tra THEM o day:
 *  - POST   : sellerId LUON la currentUserId (ADMIN hay SELLER tao cung vay) - KHONG co truong
 *    sellerId nao trong ProductRequest de client gui len, nen khong co kha nang gia mao.
 *  - PUT/DELETE {id} : SELLER chi sua/xoa duoc san pham co sellerId == currentUserId, khac se
 *    bi 403 (ForbiddenException, nem tu ProductServiceImpl.assertOwnerOrAdmin()). ADMIN bypass
 *    hoan toan (toan quyen du lieu).
 *  - GET/search (getById/getBySlug/search) : CO CHU DICH giu PUBLIC/khong kiem tra ownership -
 *    day la catalog ban hang, khach hang (chua dang nhap) can xem duoc MOI san pham dang ACTIVE
 *    de mua hang, khong phai du lieu rieng tu cua seller.
 */
@RestController
@RequestMapping("/api/catalog/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(@RequestBody @Valid ProductRequest request,
                                                                 Authentication authentication) {
        ProductResponse response = productService.create(request, currentUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Tạo sản phẩm thành công", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable Long id,
            @RequestBody @Valid ProductRequest request,
            Authentication authentication
    ) {
        ProductResponse response = productService.update(
                id, request, currentUserId(authentication), isAdmin(authentication));
        return ResponseEntity.ok(ApiResponse.success("Cập nhật sản phẩm thành công", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, Authentication authentication) {
        productService.delete(id, currentUserId(authentication), isAdmin(authentication));
        return ResponseEntity.ok(ApiResponse.success("Xóa sản phẩm thành công", null));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Lấy sản phẩm thành công", productService.getById(id)));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<ProductResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success("Lấy sản phẩm thành công", productService.getBySlug(slug)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        PageResponse<ProductResponse> response = productService.search(
                keyword, categoryId, brandId, minPrice, maxPrice, status, page, size, sortBy, sortDir
        );
        return ResponseEntity.ok(ApiResponse.success("Tìm kiếm sản phẩm thành công", response));
    }

    @GetMapping("/variants/sku/{sku}")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> getVariantBySku(@PathVariable String sku) {
        return ResponseEntity.ok(ApiResponse.success("Lấy biến thể thành công", productService.getVariantBySku(sku)));
    }

    /** Kiem tra SKU co dang ACTIVE (duoc phep ban), khong phai ton kho thuc te cua Order Service. */
    @GetMapping("/variants/sku/{sku}/sellable-check")
    public ResponseEntity<ApiResponse<Boolean>> checkSellable(
            @PathVariable String sku,
            @RequestParam int quantity
    ) {
        boolean sellable = productService.checkStockAvailable(sku, quantity);
        return ResponseEntity.ok(ApiResponse.success("Kiểm tra trạng thái bán thành công", sellable));
    }
}
