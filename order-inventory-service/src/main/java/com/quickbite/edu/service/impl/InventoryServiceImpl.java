package com.quickbite.edu.service.impl;

import com.quickbite.edu.dto.request.StockAdjustRequest;
import com.quickbite.edu.dto.request.StockInitRequest;
import com.quickbite.edu.dto.response.InventoryResponse;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.entity.Inventory;
import com.quickbite.edu.exception.BadRequestException;
import com.quickbite.edu.exception.NotFoundException;
import com.quickbite.edu.lock.RedisLockService;
import com.quickbite.edu.mapper.InventoryMapper;
import com.quickbite.edu.repository.InventoryRepository;
import com.quickbite.edu.service.interfaces.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final RedisLockService redisLockService;
    private final InventoryStockOps inventoryStockOps;

    /**
     * false (mac dinh): reserve bang atomic UPDATE - phu hop VPS 1 vCPU, khong block thread Tomcat.
     * true: bat lai Redis Distributed Lock quanh reserve (de demo dung yeu cau de bai).
     */
    @Value("${app.inventory.use-redis-lock:false}")
    private boolean useRedisLock;

    @Override
    @Transactional
    public InventoryResponse initStock(StockInitRequest request) {
        if (inventoryRepository.existsBySku(request.getSku())) {
            throw new BadRequestException("SKU [" + request.getSku() + "] da ton tai trong kho");
        }
        Inventory inventory = Inventory.builder()
                .sku(request.getSku())
                .productName(request.getProductName())
                .availableQty(request.getQuantity())
                .reservedQty(0)
                .build();
        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    @Override
    public InventoryResponse restock(String sku, StockAdjustRequest request) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BadRequestException("So luong nhap them phai lon hon 0");
        }
        return redisLockService.executeWithLock(sku,
                () -> inventoryMapper.toResponse(inventoryStockOps.restockOne(sku, request.getQuantity())));
    }

    @Override
    public InventoryResponse getBySku(String sku) {
        Inventory inventory = inventoryRepository.findBySku(sku)
                .orElseThrow(() -> new NotFoundException("Khong tim thay ton kho cho SKU [" + sku + "]"));
        return inventoryMapper.toResponse(inventory);
    }

    @Override
    public PageResponse<InventoryResponse> search(int page, int size) {
        Page<Inventory> result = inventoryRepository.findAll(PageRequest.of(page, size));
        return PageResponse.<InventoryResponse>builder()
                .content(result.getContent().stream().map(inventoryMapper::toResponse).toList())
                .pageNumber(result.getNumber())
                .pageSize(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .isLast(result.isLast())
                .build();
    }

    @Override
    public void reserveStock(Map<String, Integer> skuQuantities) {
        List<String> skus = skuQuantities.keySet().stream().sorted().toList();
        List<String> reservedSoFar = new ArrayList<>();

        java.util.function.Supplier<Void> action = () -> {
            try {
                for (String sku : skus) {
                    inventoryStockOps.reserveOne(sku, skuQuantities.get(sku));
                    reservedSoFar.add(sku);
                }
                return null;
            } catch (RuntimeException ex) {
                log.warn("Reserve stock that bai, dang compensate (tra lai) {} SKU da giu truoc do", reservedSoFar.size());
                for (String sku : reservedSoFar) {
                    inventoryStockOps.releaseOne(sku, skuQuantities.get(sku));
                }
                throw ex;
            }
        };
        if (useRedisLock) {
            redisLockService.executeWithLocks(skus, action);
        } else {
            action.get();
        }
    }

    @Override
    public void returnConfirmedStock(Map<String, Integer> skuQuantities) {
        List<String> skus = skuQuantities.keySet().stream().sorted().toList();
        skus.forEach(sku -> inventoryStockOps.returnConfirmedOne(sku, skuQuantities.get(sku)));
    }

    @Override
    public void releaseStock(Map<String, Integer> skuQuantities) {
        List<String> skus = skuQuantities.keySet().stream().sorted().toList();
        redisLockService.executeWithLocks(skus, () -> {
            skus.forEach(sku -> inventoryStockOps.releaseOne(sku, skuQuantities.get(sku)));
            return null;
        });
    }

    @Override
    public void commitStock(Map<String, Integer> skuQuantities) {
        List<String> skus = skuQuantities.keySet().stream().sorted().toList();
        redisLockService.executeWithLocks(skus, () -> {
            skus.forEach(sku -> inventoryStockOps.commitOne(sku, skuQuantities.get(sku)));
            return null;
        });
    }
}
