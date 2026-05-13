package com.fabricmanagement.production.masterdata.qualitygrade.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.qualitygrade.app.QualityGradeService;
import com.fabricmanagement.production.masterdata.qualitygrade.dto.CreateQualityGradeRequest;
import com.fabricmanagement.production.masterdata.qualitygrade.dto.QualityGradeDto;
import com.fabricmanagement.production.masterdata.qualitygrade.dto.UpdateQualityGradeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for tenant-specific quality grade definitions.
 *
 * <p>READ is open to all production departments. WRITE is restricted to QC and R&D managers.
 */
@RestController
@RequestMapping("/api/production/quality-grades")
@RequiredArgsConstructor
@Tag(name = "QualityGrade", description = "Tenant-specific quality grading system management")
public class QualityGradeController {

  private final QualityGradeService qualityGradeService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  @Operation(summary = "List quality grades for a product type")
  public ResponseEntity<ApiResponse<List<QualityGradeDto>>> findByProductType(
      @RequestParam ProductType productType) {
    List<QualityGradeDto> grades =
        qualityGradeService.findByProductType(productType).stream()
            .map(QualityGradeDto::from)
            .toList();
    return ResponseEntity.ok(ApiResponse.success(grades));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  @Operation(summary = "Get a quality grade by ID")
  public ResponseEntity<ApiResponse<QualityGradeDto>> findById(@PathVariable UUID id) {
    var grade = qualityGradeService.findById(id);
    return ResponseEntity.ok(ApiResponse.success(QualityGradeDto.from(grade)));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'quality', 'write')")
  @Operation(summary = "Create a new quality grade")
  public ResponseEntity<ApiResponse<QualityGradeDto>> create(
      @Valid @RequestBody CreateQualityGradeRequest request) {
    var grade =
        qualityGradeService.create(
            request.productType(),
            request.code(),
            request.name(),
            request.rank(),
            request.priceFactor(),
            request.saleable(),
            request.requiresApproval(),
            request.colorHex(),
            request.isDefault());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(QualityGradeDto.from(grade)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'quality', 'write')")
  @Operation(summary = "Update a quality grade (code and productType are immutable)")
  public ResponseEntity<ApiResponse<QualityGradeDto>> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateQualityGradeRequest request) {
    var grade =
        qualityGradeService.update(
            id,
            request.name(),
            request.rank(),
            request.priceFactor(),
            request.saleable(),
            request.requiresApproval(),
            request.colorHex(),
            request.isDefault());
    return ResponseEntity.ok(ApiResponse.success(QualityGradeDto.from(grade)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'quality', 'write')")
  @Operation(summary = "Deactivate (soft-delete) a quality grade")
  public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
    qualityGradeService.deactivate(id);
    return ResponseEntity.noContent().build();
  }
}
