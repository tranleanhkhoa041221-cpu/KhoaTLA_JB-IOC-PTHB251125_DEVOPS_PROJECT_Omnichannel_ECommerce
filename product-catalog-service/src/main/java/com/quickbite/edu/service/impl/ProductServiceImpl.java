package com.quickbite.edu.service.impl;

import com.quickbite.edu.dto.request.ProductRequest;
import com.quickbite.edu.dto.request.ProductVariantRequest;
import com.quickbite.edu.dto.response.CheckStockResult;
import com.quickbite.edu.dto.response.PageResponse;
import com.quickbite.edu.dto.response.ProductResponse;
import com.quickbite.edu.dto.response.ProductVariantResponse;
import com.quickbite.edu.entity.Brand;
import com.quickbite.edu.entity.Category;
import com.quickbite.edu.entity.Product;
import com.quickbite.edu.entity.ProductVariant;
import com.quickbite.edu.entity.enums.ProductStatus;
import com.quickbite.edu.exception.BadRequestException;
import com.quickbite.edu.exception.ConflictException;
import com.quickbite.edu.exception.ForbiddenException;
import com.quickbite.edu.exception.NotFoundException;
import com.quickbite.edu.mapper.CatalogMapper;
import com.quickbite.edu.repository.BrandRepository;
import com.quickbite.edu.repository.CategoryRepository;
import com.quickbite.edu.repository.ProductRepository;
import com.quickbite.edu.repository.ProductVariantRepository;
import com.quickbite.edu.service.interfaces.ProductService;
import com.quickbite.edu.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final CatalogMapper catalogMapper;

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request, Long currentUserId) {
        validateVariantSkus(request.getVariants(), null);

        Category category = resolveCategory(request.getCategoryId());
        Brand brand = resolveBrand(request.getBrandId());

        String slug = SlugUtil.uniqueSlug(request.getName(), productRepository::existsBySlug);
        Product product = Product.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                // Ownership (IDOR fix): sellerId LUON LA currentUserId lay tu SecurityContext/JWT
                // o ProductController, KHONG BAO GIO nhan tu client (ProductRequest khong co -
                // va se khong bao gio co - truong sellerId de tranh client tu gan ho cho nguoi khac).
                .sellerId(currentUserId)
                .category(category)
                .brand(brand)
                .images(request.getImages())
                .status(request.getStatus() != null ? request.getStatus() : ProductStatus.ACTIVE)
                .build();

        attachVariants(product, request.getVariants());
        Product saved = productRepository.save(product);
        return catalogMapper.toProductResponse(
                productRepository.findByIdWithVariants(saved.getId()).orElse(saved),
                true
        );
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request, Long currentUserId, boolean isAdmin) {
        Product product = productRepository.findByIdWithVariants(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm id=" + id));
        assertOwnerOrAdmin(product, currentUserId, isAdmin);

        validateVariantSkus(request.getVariants(), product.getId());

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBasePrice(request.getBasePrice());
        product.setCategory(resolveCategory(request.getCategoryId()));
        product.setBrand(resolveBrand(request.getBrandId()));
        product.setImages(request.getImages());
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }

        product.getVariants().clear();
        attachVariants(product, request.getVariants());

        Product saved = productRepository.save(product);
        return catalogMapper.toProductResponse(
                productRepository.findByIdWithVariants(saved.getId()).orElse(saved),
                true
        );
    }

    @Override
    @Transactional
    public void delete(Long id, Long currentUserId, boolean isAdmin) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm id=" + id));
        assertOwnerOrAdmin(product, currentUserId, isAdmin);
        productRepository.delete(product);
    }

    /**
     * Data Ownership / IDOR fix: SELLER chi duoc sua/xoa san pham cua CHINH MINH
     * (product.sellerId == currentUserId). ADMIN bypass hoan toan (toan quyen du lieu).
     * San pham cu/chua co sellerId (null, tao truoc migration V2) bi coi la KHONG thuoc
     * seller nao - chi ADMIN moi dong duoc vao, an toan hon la mac dinh cho phep.
     */
    private void assertOwnerOrAdmin(Product product, Long currentUserId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (product.getSellerId() == null || !product.getSellerId().equals(currentUserId)) {
            throw new ForbiddenException("Bạn không có quyền thao tác trên sản phẩm của người bán khác");
        }
    }

    @Override
    public ProductResponse getById(Long id) {
        Product product = productRepository.findByIdWithVariants(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm id=" + id));
        return catalogMapper.toProductResponse(product, true);
    }

    @Override
    public ProductResponse getBySlug(String slug) {
        Product product = productRepository.findBySlugWithVariants(slug)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm: " + slug));
        return catalogMapper.toProductResponse(product, true);
    }

    @Override
    public PageResponse<ProductResponse> search(
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
    ) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("Giá tối thiểu không được lớn hơn giá tối đa");
        }

        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        ProductStatus effectiveStatus = status != null ? status : ProductStatus.ACTIVE;

        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC,
                sortBy != null && !sortBy.isBlank() ? sortBy : "createdAt"
        );
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1), sort);

        Page<Product> result = productRepository.searchProducts(
                normalizedKeyword,
                categoryId,
                brandId,
                minPrice,
                maxPrice,
                effectiveStatus,
                pageable
        );

        Page<ProductResponse> mapped = result.map(p -> catalogMapper.toProductResponse(p, false));
        return catalogMapper.toPageResponse(mapped);
    }

    @Override
    public ProductVariantResponse getVariantBySku(String sku) {
        ProductVariant variant = productVariantRepository.findBySkuWithProduct(sku)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy SKU: " + sku));
        return catalogMapper.toVariantResponse(variant);
    }

    @Override
    public boolean checkStockAvailable(String sku, int quantity) {
        return checkStockAndPrice(sku, quantity).isAvailable();
    }

    /**
     * gRPC/REST: tra gia + snapshot ten SP. {@code available} = san pham con duoc phep ban (ACTIVE),
     * khong phan anh ton kho thuc te — Order & Inventory Service quan ly reserve/commit kho.
     */
    @Override
    public CheckStockResult checkStockAndPrice(String sku, int quantity) {
        if (sku == null || sku.isBlank()) {
            throw new BadRequestException("SKU không được để trống");
        }
        if (quantity <= 0) {
            throw new BadRequestException("Số lượng phải lớn hơn 0");
        }

        ProductVariant variant = productVariantRepository.findBySkuWithProduct(sku.trim())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy SKU: " + sku));

        Product product = variant.getProduct();
        boolean sellable = product.getStatus() == ProductStatus.ACTIVE;
        return CheckStockResult.builder()
                .available(sellable)
                .price(variant.getPrice())
                .productName(product.getName())
                .sku(variant.getSku())
                .stockQuantity(variant.getStockQuantity())
                .message(sellable
                        ? "Sản phẩm hợp lệ. Tồn kho thực tế do Order & Inventory Service quản lý."
                        : "Sản phẩm không còn được bán (trạng thái: " + product.getStatus() + ")")
                .build();
    }

    private void attachVariants(Product product, List<ProductVariantRequest> variantRequests) {
        if (variantRequests == null || variantRequests.isEmpty()) {
            return;
        }
        for (ProductVariantRequest vr : variantRequests) {
            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .sku(vr.getSku().trim())
                    .price(vr.getPrice())
                    .stockQuantity(vr.getStockQuantity() != null ? vr.getStockQuantity() : 0)
                    .attributes(vr.getAttributes())
                    .imageUrl(vr.getImageUrl())
                    .build();
            product.getVariants().add(variant);
        }
    }

    private void validateVariantSkus(List<ProductVariantRequest> variants, Long excludeProductId) {
        if (variants == null || variants.isEmpty()) {
            return;
        }
        Set<String> skus = new HashSet<>();
        for (ProductVariantRequest variant : variants) {
            String sku = variant.getSku().trim();
            if (!skus.add(sku)) {
                throw new BadRequestException("SKU trùng lặp trong cùng sản phẩm: " + sku);
            }
            productVariantRepository.findBySku(sku).ifPresent(existing -> {
                if (excludeProductId == null || !existing.getProduct().getId().equals(excludeProductId)) {
                    throw new ConflictException("SKU đã tồn tại: " + sku);
                }
            });
        }
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục id=" + categoryId));
    }

    private Brand resolveBrand(Long brandId) {
        if (brandId == null) {
            return null;
        }
        return brandRepository.findById(brandId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu id=" + brandId));
    }
}
