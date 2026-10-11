package com.quickbite.edu.config;

import com.quickbite.edu.entity.Brand;
import com.quickbite.edu.entity.Category;
import com.quickbite.edu.entity.Product;
import com.quickbite.edu.entity.ProductVariant;
import com.quickbite.edu.entity.enums.ProductStatus;
import com.quickbite.edu.repository.BrandRepository;
import com.quickbite.edu.repository.CategoryRepository;
import com.quickbite.edu.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (productRepository.count() > 0) {
            return;
        }

        Category electronics = categoryRepository.save(Category.builder()
                .name("Điện tử")
                .slug("dien-tu")
                .description("Thiết bị điện tử tiêu dùng")
                .build());

        Category phones = categoryRepository.save(Category.builder()
                .name("Điện thoại")
                .slug("dien-thoai")
                .description("Smartphone")
                .parentId(electronics.getId())
                .build());

        Brand apple = brandRepository.save(Brand.builder()
                .name("Apple")
                .slug("apple")
                .description("Thương hiệu Apple")
                .build());

        Product iphone = Product.builder()
                .name("iPhone 15 Pro")
                .slug("iphone-15-pro")
                .description("Flagship smartphone")
                .basePrice(new BigDecimal("27990000"))
                .category(phones)
                .brand(apple)
                .images("https://example.com/iphone15.jpg")
                .status(ProductStatus.ACTIVE)
                .build();

        iphone.getVariants().add(ProductVariant.builder()
                .product(iphone)
                .sku("IP15P-256-BLK")
                .price(new BigDecimal("27990000"))
                .stockQuantity(100)
                .attributes("{\"color\":\"Black\",\"storage\":\"256GB\"}")
                .build());

        iphone.getVariants().add(ProductVariant.builder()
                .product(iphone)
                .sku("IP15P-512-WHT")
                .price(new BigDecimal("31990000"))
                .stockQuantity(50)
                .attributes("{\"color\":\"White\",\"storage\":\"512GB\"}")
                .build());

        productRepository.save(iphone);
        log.info(">>> Khoi tao du lieu mau Product Catalog (category, brand, product + SKU)");
    }
}
