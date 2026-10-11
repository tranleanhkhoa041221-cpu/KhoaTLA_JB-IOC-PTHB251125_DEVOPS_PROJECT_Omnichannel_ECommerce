package com.quickbite.edu.service.interfaces;

import com.quickbite.edu.dto.request.StockAdjustRequest;
import com.quickbite.edu.dto.request.StockInitRequest;
import com.quickbite.edu.dto.response.InventoryResponse;
import com.quickbite.edu.dto.response.PageResponse;

import java.util.Map;

public interface InventoryService {

    InventoryResponse initStock(StockInitRequest request);

    InventoryResponse restock(String sku, StockAdjustRequest request);

    InventoryResponse getBySku(String sku);

    PageResponse<InventoryResponse> search(int page, int size);

    /**
     * Giu (reserve) ton kho cho nhieu SKU cung luc trong pham vi 1 don hang.
     * Neu 1 SKU bat ky khong du hang, TOAN BO cac SKU da giu truoc do trong
     * cung request se duoc tra lai (rollback) va nem InsufficientStockException.
     *
     * @param skuQuantities map sku -> so luong can giu
     */
    void reserveStock(Map<String, Integer> skuQuantities);

    /** Tra lai ton kho da giu (dung khi huy don / thanh toan that bai - compensating transaction). */
    void releaseStock(Map<String, Integer> skuQuantities);

    /** Chot don: bo phan reserved di han (khong tra lai available) vi thanh toan da thanh cong. */
    void commitStock(Map<String, Integer> skuQuantities);

    /** Hoan kho cho don DA CONFIRMED bi huy: chi cong lai availableQty (reservedQty da duoc tru luc commit). */
    void returnConfirmedStock(Map<String, Integer> skuQuantities);
}
