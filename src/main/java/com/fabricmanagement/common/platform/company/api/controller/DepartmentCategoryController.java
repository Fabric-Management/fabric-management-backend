package com.fabricmanagement.common.platform.company.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.company.app.DepartmentCategoryService;
import com.fabricmanagement.common.platform.company.domain.DepartmentCategory;
import com.fabricmanagement.common.platform.company.dto.DepartmentCategoryDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/common/department-categories")
@RequiredArgsConstructor
@Slf4j
public class DepartmentCategoryController {

    private final DepartmentCategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentCategoryDto>>> getAllCategories() {
        log.debug("Getting all department categories");

        List<DepartmentCategoryDto> categories = categoryService.findAll()
            .stream()
            .map(DepartmentCategoryDto::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentCategoryDto>> getCategory(@PathVariable UUID id) {
        log.debug("Getting department category: id={}", id);

        DepartmentCategory category = categoryService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Department category not found"));

        return ResponseEntity.ok(ApiResponse.success(DepartmentCategoryDto.from(category)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentCategoryDto>> createCategory(
            @Valid @RequestBody CreateDepartmentCategoryRequest request) {
        log.info("Creating department category: categoryName={}", request.getCategoryName());

        DepartmentCategory created = categoryService.create(
            request.getCategoryName(),
            request.getDescription(),
            request.getDisplayOrder()
        );

        return ResponseEntity.ok(ApiResponse.success(
            DepartmentCategoryDto.from(created), 
            "Department category created successfully"
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentCategoryDto>> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CreateDepartmentCategoryRequest request) {
        log.info("Updating department category: id={}", id);

        DepartmentCategory updated = categoryService.update(
            id,
            request.getCategoryName(),
            request.getDescription(),
            request.getDisplayOrder()
        );

        return ResponseEntity.ok(ApiResponse.success(
            DepartmentCategoryDto.from(updated), 
            "Department category updated successfully"
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateCategory(@PathVariable UUID id) {
        log.info("Deactivating department category: id={}", id);

        categoryService.deactivate(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Department category deactivated successfully"));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class CreateDepartmentCategoryRequest {
        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name must not exceed 100 characters")
        private String categoryName;

        @Size(max = 500, message = "Description must not exceed 500 characters")
        private String description;

        private Integer displayOrder;
    }
}

