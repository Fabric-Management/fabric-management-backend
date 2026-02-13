package com.fabricmanagement.common.platform.organization.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.organization.domain.Position;
import com.fabricmanagement.common.platform.organization.dto.PositionDto;
import com.fabricmanagement.common.platform.organization.infra.repository.PositionRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/positions")
@RequiredArgsConstructor
@Slf4j
public class PositionController {

  private final PositionRepository positionRepository;

  /**
   * Get all positions for current tenant.
   *
   * <p>Returns active positions only, ordered by display order and position name.
   *
   * <p><b>Hybrid Model:</b> Returns tenant-level positions only. Platform-level positions are
   * reference catalog and are copied to tenants during seeding. Tenant-specific positions are what
   * users interact with.
   */
  @GetMapping
  public ResponseEntity<ApiResponse<List<PositionDto>>> getAllPositions() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting all positions: tenantId={}", tenantId);

    // Tenant-level positions only (platform-level positions are reference, not shown)
    List<PositionDto> positions =
        positionRepository.findByTenantId(tenantId).stream()
            .map(PositionDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(positions));
  }

  /** Get all positions for a specific department. */
  @GetMapping("/department/{departmentId}")
  public ResponseEntity<ApiResponse<List<PositionDto>>> getPositionsByDepartment(
      @PathVariable UUID departmentId) {
    log.debug("Getting positions by department: departmentId={}", departmentId);

    List<PositionDto> positions =
        positionRepository.findByDepartmentId(departmentId).stream()
            .map(PositionDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(positions));
  }

  /** Get position by ID. */
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<PositionDto>> getPosition(@PathVariable UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting position: tenantId={}, id={}", tenantId, id);

    Position position =
        positionRepository
            .findById(id)
            .filter(p -> p.getTenantId().equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("Position not found"));

    return ResponseEntity.ok(ApiResponse.success(PositionDto.from(position)));
  }
}
