package com.quickbite.edu.service.impl;

import com.quickbite.edu.dto.request.CategoryRequest;
import com.quickbite.edu.dto.response.CategoryResponse;
import com.quickbite.edu.entity.Category;
import com.quickbite.edu.exception.BadRequestException;
import com.quickbite.edu.exception.ConflictException;
import com.quickbite.edu.exception.NotFoundException;
import com.quickbite.edu.mapper.CatalogMapper;
import com.quickbite.edu.repository.CategoryRepository;
import com.quickbite.edu.service.interfaces.CategoryService;
import com.quickbite.edu.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CatalogMapper catalogMapper;

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new ConflictException("Danh mục đã tồn tại: " + request.getName());
        }
        validateParent(request.getParentId());

        String slug = SlugUtil.uniqueSlug(request.getName(), categoryRepository::existsBySlug);
        Category category = Category.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .parentId(request.getParentId())
                .build();

        return catalogMapper.toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findCategoryOrThrow(id);

        if (!category.getName().equalsIgnoreCase(request.getName())
                && categoryRepository.existsByName(request.getName())) {
            throw new ConflictException("Danh mục đã tồn tại: " + request.getName());
        }
        validateParent(request.getParentId());
        if (request.getParentId() != null && request.getParentId().equals(id)) {
            throw new BadRequestException("Danh mục không thể là cha của chính nó");
        }

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setParentId(request.getParentId());
        if (!category.getSlug().startsWith(SlugUtil.toSlug(request.getName()))) {
            category.setSlug(SlugUtil.uniqueSlug(request.getName(), categoryRepository::existsBySlug));
        }

        return catalogMapper.toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = findCategoryOrThrow(id);
        categoryRepository.delete(category);
    }

    @Override
    public CategoryResponse getById(Long id) {
        return catalogMapper.toCategoryResponse(findCategoryOrThrow(id));
    }

    @Override
    public CategoryResponse getBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục: " + slug));
        return catalogMapper.toCategoryResponse(category);
    }

    @Override
    public List<CategoryResponse> getRootCategories() {
        return categoryRepository.findByParentIdIsNull().stream()
                .map(catalogMapper::toCategoryResponse)
                .toList();
    }

    @Override
    public List<CategoryResponse> getChildren(Long parentId) {
        findCategoryOrThrow(parentId);
        return categoryRepository.findByParentId(parentId).stream()
                .map(catalogMapper::toCategoryResponse)
                .toList();
    }

    @Override
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream()
                .map(catalogMapper::toCategoryResponse)
                .toList();
    }

    private Category findCategoryOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục id=" + id));
    }

    private void validateParent(Long parentId) {
        if (parentId == null) {
            return;
        }
        if (!categoryRepository.existsById(parentId)) {
            throw new NotFoundException("Không tìm thấy danh mục cha id=" + parentId);
        }
    }
}
