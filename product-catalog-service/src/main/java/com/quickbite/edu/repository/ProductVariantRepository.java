package com.quickbite.edu.repository;

import com.quickbite.edu.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findBySku(String sku);

    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product WHERE v.sku = :sku")
    Optional<ProductVariant> findBySkuWithProduct(@Param("sku") String sku);
    boolean existsBySku(String sku);
    List<ProductVariant> findByProductId(Long productId);
    void deleteByProductId(Long productId);
}