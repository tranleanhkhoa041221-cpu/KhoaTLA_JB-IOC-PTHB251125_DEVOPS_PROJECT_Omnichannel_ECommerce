package com.quickbite.edu.service.interfaces;

import com.quickbite.edu.dto.request.ProductRequest;
import com.quickbite.edu.dto.response.CheckStockResult;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.dto.response.ProductResponse;
import com.quickbite.edu.dto.response.ProductVariantResponse;
import com.quickbite.edu.entity.enums.ProductStatus;

import java.math.BigDecimal;

public interface ProductService {

    /**
     * @param currentUserId ID cua nguoi dang tao (ADMIN hoac SELLER), lay tu SecurityContext/JWT
     *                      o Controller - KHONG BAO GIO nhan tu client - duoc gan lam Product.sellerId.
     */
    ProductResponse create(ProductRequest request, Long currentUserId);

    /**
     * @param currentUserId ID nguoi goi hien tai.
     * @param isAdmin       true neu nguoi goi co role ADMIN (bypass kiem tra ownership, sua duoc
     *                      san pham cua BAT KY seller nao). SELLER (isAdmin=false) chi duoc sua
     *                      san pham co sellerId == currentUserId, khac se bi tu choi 403.
     */
    ProductResponse update(Long id, ProductRequest request, Long currentUserId, boolean isAdmin);

    /** Xem javadoc {@link #update(Long, ProductRequest, Long, boolean)} - cung quy tac ownership. */
    void delete(Long id, Long currentUserId, boolean isAdmin);

    ProductResponse getById(Long id);

    ProductResponse getBySlug(String slug);

    PageResponse<ProductResponse> search(
            String keyword,
            Long categoryId,
            Long brandId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            ProductStatus status,
            int page,
            int size,
            String sortBy,
            String sortDir
    );

    ProductVariantResponse getVariantBySku(String sku);

    boolean checkStockAvailable(String sku, int quantity);

    CheckStockResult checkStockAndPrice(String sku, int quantity);
}
