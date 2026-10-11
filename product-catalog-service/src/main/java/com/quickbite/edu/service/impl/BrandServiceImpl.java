package com.quickbite.edu.service.impl;

import com.quickbite.edu.dto.request.BrandRequest;
import com.quickbite.edu.dto.response.BrandResponse;
import com.quickbite.edu.entity.Brand;
import com.quickbite.edu.exception.ConflictException;
import com.quickbite.edu.exception.NotFoundException;
import com.quickbite.edu.mapper.CatalogMapper;
import com.quickbite.edu.repository.BrandRepository;
import com.quickbite.edu.service.interfaces.BrandService;
import com.quickbite.edu.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;
    private final CatalogMapper catalogMapper;

    @Override
    @Transactional
    public BrandResponse create(BrandRequest request) {
        if (brandRepository.existsByName(request.getName())) {
            throw new ConflictException("Thương hiệu đã tồn tại: " + request.getName());
        }

        String slug = SlugUtil.uniqueSlug(request.getName(), brandRepository::existsBySlug);
        Brand brand = Brand.builder()
                .name(request.getName())
                .slug(slug)
                .logoUrl(request.getLogoUrl())
                .description(request.getDescription())
                .build();

        return catalogMapper.toBrandResponse(brandRepository.save(brand));
    }

    @Override
    @Transactional
    public BrandResponse update(Long id, BrandRequest request) {
        Brand brand = findBrandOrThrow(id);

        if (!brand.getName().equalsIgnoreCase(request.getName())
                && brandRepository.existsByName(request.getName())) {
            throw new ConflictException("Thương hiệu đã tồn tại: " + request.getName());
        }

        brand.setName(request.getName());
        brand.setLogoUrl(request.getLogoUrl());
        brand.setDescription(request.getDescription());
        if (!brand.getSlug().startsWith(SlugUtil.toSlug(request.getName()))) {
            brand.setSlug(SlugUtil.uniqueSlug(request.getName(), brandRepository::existsBySlug));
        }

        return catalogMapper.toBrandResponse(brandRepository.save(brand));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Brand brand = findBrandOrThrow(id);
        brandRepository.delete(brand);
    }

    @Override
    public BrandResponse getById(Long id) {
        return catalogMapper.toBrandResponse(findBrandOrThrow(id));
    }

    @Override
    public BrandResponse getBySlug(String slug) {
        Brand brand = brandRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu: " + slug));
        return catalogMapper.toBrandResponse(brand);
    }

    @Override
    public List<BrandResponse> getAll() {
        return brandRepository.findAll().stream()
                .map(catalogMapper::toBrandResponse)
                .toList();
    }

    private Brand findBrandOrThrow(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu id=" + id));
    }
}
