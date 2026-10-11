package com.quickbite.edu.service.interfaces;

import com.quickbite.edu.dto.request.BrandRequest;
import com.quickbite.edu.dto.response.BrandResponse;

import java.util.List;

public interface BrandService {

    BrandResponse create(BrandRequest request);

    BrandResponse update(Long id, BrandRequest request);

    void delete(Long id);

    BrandResponse getById(Long id);

    BrandResponse getBySlug(String slug);

    List<BrandResponse> getAll();
}
