package com.fabricmanagement.production.execution.output.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.execution.output.app.ProductionOutputService;
import com.fabricmanagement.production.execution.output.domain.ProductionOutputRecord;
import com.fabricmanagement.production.execution.output.dto.AddOutputItemRequest;
import com.fabricmanagement.production.execution.output.dto.CreateProductionOutputRequest;
import com.fabricmanagement.production.execution.output.dto.ProductionOutputDto;
import com.fabricmanagement.production.execution.output.mapper.ProductionOutputMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/production/output-records")
@RequiredArgsConstructor
@Tag(name = "Production Output", description = "Production Output operations")
public class ProductionOutputController {

  private final ProductionOutputService service;
  private final ProductionOutputMapper mapper;

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  public ResponseEntity<ApiResponse<ProductionOutputDto>> create(
      @Valid @RequestBody CreateProductionOutputRequest request) {
    ProductionOutputRecord record = service.create(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(mapper.toDto(record)));
  }

  @PostMapping("/{id}/items")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  public ResponseEntity<ApiResponse<ProductionOutputDto>> addItem(
      @PathVariable UUID id, @Valid @RequestBody AddOutputItemRequest request) {
    ProductionOutputRecord record = service.addItem(id, request);
    return ResponseEntity.ok(ApiResponse.success(mapper.toDto(record)));
  }

  @DeleteMapping("/{id}/items/{itemId}")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  public ResponseEntity<ApiResponse<ProductionOutputDto>> removeItem(
      @PathVariable UUID id, @PathVariable UUID itemId) {
    ProductionOutputRecord record = service.removeItem(id, itemId);
    return ResponseEntity.ok(ApiResponse.success(mapper.toDto(record)));
  }

  @PostMapping("/{id}/confirm")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  public ResponseEntity<ApiResponse<ProductionOutputDto>> confirm(@PathVariable UUID id) {
    ProductionOutputRecord record = service.confirm(id);
    return ResponseEntity.ok(ApiResponse.success(mapper.toDto(record)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  public ResponseEntity<ApiResponse<ProductionOutputDto>> getById(@PathVariable UUID id) {
    ProductionOutputRecord record = service.getById(id);
    return ResponseEntity.ok(ApiResponse.success(mapper.toDto(record)));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  public ResponseEntity<ApiResponse<List<ProductionOutputDto>>> getByWorkOrderId(
      @RequestParam UUID workOrderId) {
    List<ProductionOutputRecord> records = service.getByWorkOrderId(workOrderId);
    return ResponseEntity.ok(ApiResponse.success(records.stream().map(mapper::toDto).toList()));
  }
}
