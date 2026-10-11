package com.quickbite.edu.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Ton kho noi bo cua Order & Inventory Service (Database Per Service Pattern).
 * Bang nay la nguon "su that" (source of truth) de giu/tra hang khi dat don,
 * doc lap voi truong stockQuantity ben Product Catalog Service (chi dung de hien thi).
 */
@Entity
@Table(name = "inventories", indexes = {
        @Index(name = "idx_inventories_sku", columnList = "sku", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    /** So luong con co the ban */
    @Column(name = "available_qty", nullable = false)
    @Builder.Default
    private Integer availableQty = 0;

    /** So luong da bi giu cho cac don PENDING */
    @Column(name = "reserved_qty", nullable = false)
    @Builder.Default
    private Integer reservedQty = 0;

    /** Optimistic lock - lop bao ve thu 2 ngoai Redis distributed lock */
    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void reserve(int quantity) {
        this.availableQty -= quantity;
        this.reservedQty += quantity;
    }

    public void release(int quantity) {
        this.availableQty += quantity;
        this.reservedQty = Math.max(0, this.reservedQty - quantity);
    }

    public void restock(int quantity) {
        this.availableQty += quantity;
    }

    public boolean hasEnough(int quantity) {
        return this.availableQty >= quantity;
    }
}
