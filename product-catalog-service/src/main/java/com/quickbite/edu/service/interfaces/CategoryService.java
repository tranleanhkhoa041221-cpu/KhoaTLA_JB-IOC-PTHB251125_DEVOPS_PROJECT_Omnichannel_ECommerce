package com.quickbite.edu.service.interfaces;

import com.quickbite.edu.dto.request.CategoryRequest;
import com.quickbite.edu.dto.response.CategoryResponse;

import java.util.List;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Long id, CategoryRequest request);

    void delete(Long id);

    CategoryResponse getById(Long id);

    CategoryResponse getBySlug(String slug);

    List<CategoryResponse> getRootCategories();

    List<CategoryResponse> getChildren(Long parentId);

    List<CategoryResponse> getAll();
}
