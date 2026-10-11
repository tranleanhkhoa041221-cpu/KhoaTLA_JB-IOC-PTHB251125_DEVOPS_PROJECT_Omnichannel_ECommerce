package com.quickbite.edu.repository;

import com.quickbite.edu.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findBySku(String sku);

    boolean existsBySku(String sku);

    /**
     * SELECT ... FOR UPDATE - Pessimistic Locking o tang Database, dung ket hop
     * voi Redis Distributed Lock o tang ung dung de chong Race Condition khi
     * nhieu request cung tru kho 1 SKU tai cung 1 thoi diem (VD: Flash Sale).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.sku = :sku")
    Optional<Inventory> findBySkuForUpdate(String sku);

    /**
     * Giu kho NGUYEN TU bang 1 cau UPDATE co dieu kien (khong can Redis lock, khong SELECT FOR UPDATE):
     * Postgres tu khoa dong khi UPDATE; dieu kien available_qty >= :q bao dam khong oversell.
     * Tra ve 1 neu giu duoc, 0 neu het hang / khong co SKU.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Inventory i set i.availableQty = i.availableQty - :q, i.reservedQty = i.reservedQty + :q, "
            + "i.version = i.version + 1 where i.sku = :sku and i.availableQty >= :q")
    int tryReserve(String sku, int q);
}
