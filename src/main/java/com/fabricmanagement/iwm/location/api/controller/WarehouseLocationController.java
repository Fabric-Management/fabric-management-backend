package com.fabricmanagement.iwm.location.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.iwm.location.app.WarehouseLocationService;
import com.fabricmanagement.iwm.location.domain.LocationStatus;
import com.fabricmanagement.iwm.location.domain.StorageCondition;
import com.fabricmanagement.iwm.location.domain.WarehouseLocationType;
import com.fabricmanagement.iwm.location.dto.ChangeLocationStatusRequest;
import com.fabricmanagement.iwm.location.dto.CreateWarehouseLocationRequest;
import com.fabricmanagement.iwm.location.dto.UpdateWarehouseLocationRequest;
import com.fabricmanagement.iwm.location.dto.WarehouseLocationDto;
import com.fabricmanagement.iwm.location.dto.WarehouseLocationTreeDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iwm/locations")
@RequiredArgsConstructor
@Tag(name = "IWM Location", description = "IWM Warehouse Location Management API")
@Slf4j
public class WarehouseLocationController {

  private final WarehouseLocationService service;

  // ── Commands ────────────────────────────────────────────────────────────────

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'materials', 'write')")
  public ResponseEntity<ApiResponse<WarehouseLocationDto>> create(
      @Valid @RequestBody CreateWarehouseLocationRequest request) {
    WarehouseLocationDto location = service.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(location));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'write')")
  public ResponseEntity<ApiResponse<WarehouseLocationDto>> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateWarehouseLocationRequest request) {
    WarehouseLocationDto location = service.update(id, request);
    return ResponseEntity.ok(ApiResponse.success(location));
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("@auth.can(authentication, 'materials', 'write')")
  public ResponseEntity<ApiResponse<WarehouseLocationDto>> changeStatus(
      @PathVariable UUID id, @Valid @RequestBody ChangeLocationStatusRequest request) {
    WarehouseLocationDto location = service.changeStatus(id, request);
    return ResponseEntity.ok(ApiResponse.success(location));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'write')")
  public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
    service.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  // ── Queries ─────────────────────────────────────────────────────────────────

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<WarehouseLocationDto>>> getAll() {
    List<WarehouseLocationDto> locations = service.getAll();
    return ResponseEntity.ok(ApiResponse.success(locations));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<WarehouseLocationDto>> getById(@PathVariable UUID id) {
    WarehouseLocationDto location = service.getById(id);
    return ResponseEntity.ok(ApiResponse.success(location));
  }

  @GetMapping("/{id}/children")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<WarehouseLocationDto>>> getChildren(
      @PathVariable UUID id) {
    List<WarehouseLocationDto> children = service.getChildren(id);
    return ResponseEntity.ok(ApiResponse.success(children));
  }

  @GetMapping("/tree")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<WarehouseLocationTreeDto>>> getTree() {
    List<WarehouseLocationTreeDto> tree = service.getTree();
    return ResponseEntity.ok(ApiResponse.success(tree));
  }

  @GetMapping("/{id}/subtree")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<WarehouseLocationTreeDto>> getSubTree(@PathVariable UUID id) {
    WarehouseLocationTreeDto subtree = service.getSubTree(id);
    return ResponseEntity.ok(ApiResponse.success(subtree));
  }

  @GetMapping("/machines")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<WarehouseLocationDto>>> getMachineLocations() {
    List<WarehouseLocationDto> locations = service.getMachineLocations();
    return ResponseEntity.ok(ApiResponse.success(locations));
  }

  @GetMapping("/type/{type}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<WarehouseLocationDto>>> getByType(
      @PathVariable WarehouseLocationType type) {
    List<WarehouseLocationDto> locations = service.getByType(type);
    return ResponseEntity.ok(ApiResponse.success(locations));
  }

  @GetMapping("/status/{status}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<WarehouseLocationDto>>> getByStatus(
      @PathVariable LocationStatus status) {
    List<WarehouseLocationDto> locations = service.getByStatus(status);
    return ResponseEntity.ok(ApiResponse.success(locations));
  }

  @GetMapping("/search")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<WarehouseLocationDto>>> search(
      @RequestParam(required = false) String q) {
    List<WarehouseLocationDto> locations = service.search(q);
    return ResponseEntity.ok(ApiResponse.success(locations));
  }

  @GetMapping("/filter")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<WarehouseLocationDto>>> filter(
      @RequestParam(required = false) WarehouseLocationType type,
      @RequestParam(required = false) LocationStatus status,
      @RequestParam(required = false) StorageCondition storageCondition) {
    List<WarehouseLocationDto> locations = service.filter(type, status, storageCondition);
    return ResponseEntity.ok(ApiResponse.success(locations));
  }

  @GetMapping("/available")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<WarehouseLocationDto>>> findAvailable(
      @RequestParam(required = false) BigDecimal requiredWeight,
      @RequestParam(required = false) WarehouseLocationType type) {
    List<WarehouseLocationDto> locations = service.findAvailableLocations(requiredWeight, type);
    return ResponseEntity.ok(ApiResponse.success(locations));
  }

  @GetMapping("/{id}/capacity-check")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<Boolean>> checkCapacity(
      @PathVariable UUID id,
      @RequestParam(required = false) BigDecimal weight,
      @RequestParam(required = false) BigDecimal volume) {
    boolean hasCapacity = service.checkCapacity(id, weight, volume);
    return ResponseEntity.ok(ApiResponse.success(hasCapacity));
  }
}
