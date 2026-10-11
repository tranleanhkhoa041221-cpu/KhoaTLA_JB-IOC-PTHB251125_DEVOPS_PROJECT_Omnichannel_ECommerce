package com.quickbite.edu.controller;

import com.quickbite.edu.dto.request.StockAdjustRequest;
import com.quickbite.edu.dto.request.StockInitRequest;
import com.quickbite.edu.dto.response.ApiResponse;
import com.quickbite.edu.dto.response.InventoryResponse;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.service.interfaces.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<InventoryResponse>> initStock(@RequestBody @Valid StockInitRequest request) {
        InventoryResponse response = inventoryService.initStock(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Khoi tao ton kho thanh cong", response));
    }

    @PutMapping("/{sku}/restock")
    public ResponseEntity<ApiResponse<InventoryResponse>> restock(
            @PathVariable String sku,
            @RequestBody @Valid StockAdjustRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Nhap them kho thanh cong", inventoryService.restock(sku, request)));
    }

    @GetMapping("/{sku}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getBySku(@PathVariable String sku) {
        return ResponseEntity.ok(ApiResponse.success("Lay ton kho thanh cong", inventoryService.getBySku(sku)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<InventoryResponse>>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Lay danh sach ton kho thanh cong", inventoryService.search(page, size)));
    }
}
