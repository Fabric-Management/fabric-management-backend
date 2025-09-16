package com.fabricmanagement.common.core.infrastructure.web;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import com.fabricmanagement.common.core.application.dto.BaseDto;
import com.fabricmanagement.common.core.application.dto.PageRequest;
import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.common.core.application.service.BaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Base REST controller providing common CRUD endpoints.
 * All controllers should extend this class.
 *
 * @param <D> DTO type
 * @param <ID> ID type
 * @param <S> Service type
 */
@RequiredArgsConstructor
public abstract class BaseController<D extends BaseDto, ID, S extends BaseService<D, ID>> {

    protected final S service;

    @PostMapping
    public ResponseEntity<ApiResponse<D>> create(@Valid @RequestBody D dto) {
        D created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(created, "Entity created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<D>> update(
            @PathVariable ID id,
            @Valid @RequestBody D dto) {
        D updated = service.update(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Entity updated successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<D>> getById(@PathVariable ID id) {
        D entity = service.getById(id);
        return ResponseEntity.ok(ApiResponse.success(entity));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<D>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<String> sort) {

        PageRequest pageRequest = PageRequest.builder()
            .page(page)
            .size(size)
            .build();

        PageResponse<D> result = service.findAll(pageRequest);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable ID id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> count() {
        long count = service.count();
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    @GetMapping("/exists/{id}")
    public ResponseEntity<ApiResponse<Boolean>> exists(@PathVariable ID id) {
        boolean exists = service.existsById(id);
        return ResponseEntity.ok(ApiResponse.success(exists));
    }
}
