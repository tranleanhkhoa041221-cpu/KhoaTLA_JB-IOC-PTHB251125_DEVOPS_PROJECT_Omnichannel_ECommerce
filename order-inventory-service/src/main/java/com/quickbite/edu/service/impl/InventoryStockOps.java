package com.quickbite.edu.service.impl;

import com.quickbite.edu.entity.Inventory;
import com.quickbite.edu.exception.InsufficientStockException;
import com.quickbite.edu.exception.NotFoundException;
import com.quickbite.edu.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cac thao tac ghi du lieu ton kho o muc 1 SKU, moi thao tac la 1 transaction
 * NGAN va DOC LAP (Propagation.REQUIRES_NEW) de:
 *  1) Giu row-lock (SELECT ... FOR UPDATE) trong thoi gian ngan nhat co the.
 *  2) Cho phep InventoryServiceImpl goi tuan tu nhieu SKU, neu 1 SKU giua
 *     chung that bai thi cac SKU da xu ly TRUOC DO van giu nguyen ket qua
 *     (khong bi rollback theo transaction cha) de tang InventoryServiceImpl
 *     tu quyet dinh compensate (tra lai kho) mot cach tuong minh.
 *
 * Duoc tach thanh bean rieng (khac InventoryServiceImpl) de dam bao Spring AOP
 * Proxy cho @Transactional hoat dong dung (tranh loi self-invocation).
 */
@Component
@RequiredArgsConstructor
public class InventoryStockOps {

    private final InventoryRepository inventoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveOne(String sku, int quantity) {
        // Atomic conditional UPDATE: 1 round-trip, giu row-lock cuc ngan, khong oversell.
        if (inventoryRepository.tryReserve(sku, quantity) == 1) {
            return;
        }
        if (!inventoryRepository.existsBySku(sku)) {
            throw new NotFoundException("Khong tim thay ton kho cho SKU [" + sku + "]");
        }
        throw new InsufficientStockException(sku);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void returnConfirmedOne(String sku, int quantity) {
        inventoryRepository.findBySkuForUpdate(sku).ifPresent(inventory -> {
            inventory.restock(quantity);
            inventoryRepository.save(inventory);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseOne(String sku, int quantity) {
        inventoryRepository.findBySkuForUpdate(sku).ifPresent(inventory -> {
            inventory.release(quantity);
            inventoryRepository.save(inventory);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Inventory restockOne(String sku, int quantity) {
        Inventory inventory = inventoryRepository.findBySkuForUpdate(sku)
                .orElseThrow(() -> new NotFoundException("Khong tim thay ton kho cho SKU [" + sku + "]"));
        inventory.restock(quantity);
        return inventoryRepository.save(inventory);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void commitOne(String sku, int quantity) {
        inventoryRepository.findBySkuForUpdate(sku).ifPresent(inventory -> {
            inventory.setReservedQty(Math.max(0, inventory.getReservedQty() - quantity));
            inventoryRepository.save(inventory);
        });
    }
}
