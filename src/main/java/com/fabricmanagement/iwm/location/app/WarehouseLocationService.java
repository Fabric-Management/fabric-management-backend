package com.fabricmanagement.iwm.location.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.common.infrastructure.web.exception.OptimisticLockConflictException;
import com.fabricmanagement.iwm.common.exception.IwmDomainException;
import com.fabricmanagement.iwm.location.domain.LocationStatus;
import com.fabricmanagement.iwm.location.domain.StorageCondition;
import com.fabricmanagement.iwm.location.domain.WarehouseLocation;
import com.fabricmanagement.iwm.location.domain.WarehouseLocationType;
import com.fabricmanagement.iwm.location.dto.ChangeLocationStatusRequest;
import com.fabricmanagement.iwm.location.dto.CreateWarehouseLocationRequest;
import com.fabricmanagement.iwm.location.dto.UpdateWarehouseLocationRequest;
import com.fabricmanagement.iwm.location.dto.WarehouseLocationDto;
import com.fabricmanagement.iwm.location.dto.WarehouseLocationTreeDto;
import com.fabricmanagement.iwm.location.infra.repository.WarehouseLocationRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseLocationService {

  private final WarehouseLocationRepository repository;

  private static final Map<WarehouseLocationType, Set<WarehouseLocationType>> ALLOWED_PARENTS =
      Map.of(
          WarehouseLocationType.WAREHOUSE, Set.of(),
          WarehouseLocationType.ZONE, Set.of(WarehouseLocationType.WAREHOUSE),
          WarehouseLocationType.AISLE, Set.of(WarehouseLocationType.ZONE),
          WarehouseLocationType.BIN, Set.of(WarehouseLocationType.AISLE),
          WarehouseLocationType.MACHINE,
              Set.of(
                  WarehouseLocationType.WAREHOUSE,
                  WarehouseLocationType.ZONE,
                  WarehouseLocationType.AISLE),
          WarehouseLocationType.PRODUCTION_LINE,
              Set.of(
                  WarehouseLocationType.WAREHOUSE,
                  WarehouseLocationType.ZONE,
                  WarehouseLocationType.AISLE));

  // ── Create ──────────────────────────────────────────────────────────────────

  @Transactional
  public WarehouseLocationDto create(CreateWarehouseLocationRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    log.debug("Creating warehouse location: tenantId={}, code={}", tenantId, request.getCode());

    validateCodeUnique(tenantId, request.getCode());
    if (request.getBarcode() != null && !request.getBarcode().isBlank()) {
      validateBarcodeUnique(tenantId, request.getBarcode());
    }

    WarehouseLocation parent = null;
    if (request.getParentId() != null) {
      parent = findEntityById(request.getParentId());
    }
    validateHierarchy(request.getType(), parent);

    WarehouseLocation location =
        new WarehouseLocation(
            request.getParentId(),
            request.getCode(),
            request.getName(),
            request.getDescription(),
            request.getType(),
            request.getStorageCondition(),
            request.getBarcode(),
            request.getAddressId(),
            request.getMaxWeightKg(),
            request.getMaxVolumeM3(),
            request.getSortOrder(),
            request.getLinkedMachineId(),
            request.isQualityArea());
    location.setTenantId(tenantId);

    String path = buildPath(parent, request.getCode());
    int level = parent != null ? parent.getLevel() + 1 : 0;
    location.assignPath(path, level);

    location = repository.save(location);
    log.info(
        "Created warehouse location: id={}, code={}, path={}",
        location.getId(),
        location.getCode(),
        path);
    return WarehouseLocationDto.from(location);
  }

  // ── Update ──────────────────────────────────────────────────────────────────

  @Transactional
  public WarehouseLocationDto update(UUID id, UpdateWarehouseLocationRequest request) {
    WarehouseLocation location = findEntityById(id);
    checkVersion("WarehouseLocation", id, request.getVersion(), location.getVersion());

    if (request.getBarcode() != null
        && !request.getBarcode().isBlank()
        && !request.getBarcode().equals(location.getBarcode())) {
      validateBarcodeUnique(location.getTenantId(), request.getBarcode());
    }

    validateCapacityFloor(location, request.getMaxWeightKg(), request.getMaxVolumeM3());

    location.update(
        request.getName(),
        request.getDescription(),
        request.getStorageCondition(),
        request.getBarcode(),
        request.getAddressId(),
        request.getMaxWeightKg(),
        request.getMaxVolumeM3(),
        request.getSortOrder(),
        request.getLinkedMachineId(),
        request.getQualityArea() != null ? request.getQualityArea() : location.isQualityArea());

    location = repository.save(location);
    log.info("Updated warehouse location: id={}, code={}", location.getId(), location.getCode());
    return WarehouseLocationDto.from(location);
  }

  // ── Status Change ───────────────────────────────────────────────────────────

  @Transactional
  public WarehouseLocationDto changeStatus(UUID id, ChangeLocationStatusRequest request) {
    WarehouseLocation location = findEntityById(id);
    checkVersion("WarehouseLocation", id, request.getVersion(), location.getVersion());

    if (request.getStatus() == LocationStatus.BLOCKED
        || request.getStatus() == LocationStatus.MAINTENANCE) {
      long childCount = repository.countActiveChildren(id);
      if (childCount > 0) {
        log.info(
            "Blocking location {} which has {} active children — consider blocking them too",
            location.getCode(),
            childCount);
      }
    }

    location.changeStatus(request.getStatus());
    location = repository.save(location);
    log.info(
        "Changed location status: id={}, code={}, newStatus={}, reason={}",
        location.getId(),
        location.getCode(),
        request.getStatus(),
        request.getReason());
    return WarehouseLocationDto.from(location);
  }

  // ── Deactivate (Soft Delete) ────────────────────────────────────────────────

  @Transactional
  public void deactivate(UUID id) {
    WarehouseLocation location = findEntityById(id);

    long childCount = repository.countActiveChildren(id);
    if (childCount > 0) {
      throw new IwmDomainException(
          String.format(
              "Cannot deactivate location '%s' — it has %d active child location(s). Deactivate or move them first.",
              location.getCode(), childCount));
    }

    BigDecimal cw =
        location.getCurrentWeightKg() != null ? location.getCurrentWeightKg() : BigDecimal.ZERO;
    BigDecimal cv =
        location.getCurrentVolumeM3() != null ? location.getCurrentVolumeM3() : BigDecimal.ZERO;
    if (cw.compareTo(BigDecimal.ZERO) > 0 || cv.compareTo(BigDecimal.ZERO) > 0) {
      throw new IwmDomainException(
          String.format(
              "Cannot deactivate location '%s' — it still holds inventory (weight=%.3f kg, volume=%.3f m³). Transfer inventory first.",
              location.getCode(), cw, cv));
    }

    location.delete();
    repository.save(location);
    log.info("Deactivated warehouse location: id={}, code={}", id, location.getCode());
  }

  // ── Queries ─────────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<WarehouseLocationDto> getAll() {
    return getAll(null);
  }

  @Transactional(readOnly = true)
  public List<WarehouseLocationDto> getAll(Boolean qualityArea) {
    if (qualityArea != null) {
      return repository
          .findByQualityAreaAndIsActiveTrueOrderBySortOrderAscNameAsc(qualityArea)
          .stream()
          .filter(location -> !qualityArea || location.isOperational())
          .map(WarehouseLocationDto::from)
          .toList();
    }
    return repository.findByIsActiveTrueOrderBySortOrderAscNameAsc().stream()
        .map(WarehouseLocationDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public WarehouseLocationDto getById(UUID id) {
    return WarehouseLocationDto.from(findEntityById(id));
  }

  @Transactional(readOnly = true)
  public List<WarehouseLocationDto> getChildren(UUID parentId) {
    return repository.findByParentIdAndIsActiveTrue(parentId).stream()
        .sorted(
            Comparator.comparingInt(
                    (WarehouseLocation w) -> w.getSortOrder() != null ? w.getSortOrder() : 0)
                .thenComparing(WarehouseLocation::getName))
        .map(WarehouseLocationDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<WarehouseLocationDto> getMachineLocations() {
    return repository
        .findByTypeInAndIsActiveTrue(
            List.of(WarehouseLocationType.MACHINE, WarehouseLocationType.PRODUCTION_LINE))
        .stream()
        .map(WarehouseLocationDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<WarehouseLocationDto> getByType(WarehouseLocationType type) {
    return repository.findByTypeAndIsActiveTrue(type).stream()
        .map(WarehouseLocationDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<WarehouseLocationDto> getByStatus(LocationStatus status) {
    return repository.findByStatusAndIsActiveTrue(status).stream()
        .map(WarehouseLocationDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<WarehouseLocationDto> search(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    String sanitized = query.trim().replace("%", "\\%").replace("_", "\\_");
    return repository.search(sanitized).stream().map(WarehouseLocationDto::from).toList();
  }

  @Transactional(readOnly = true)
  public List<WarehouseLocationDto> filter(
      WarehouseLocationType type, LocationStatus status, StorageCondition storageCondition) {
    return repository.findByFilters(type, status, storageCondition).stream()
        .map(WarehouseLocationDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<WarehouseLocationDto> findAvailableLocations(
      BigDecimal requiredWeight, WarehouseLocationType type) {
    BigDecimal weight = requiredWeight != null ? requiredWeight : BigDecimal.ZERO;
    return repository.findAvailableByCapacity(weight, type, LocationStatus.AVAILABLE).stream()
        .map(WarehouseLocationDto::from)
        .toList();
  }

  // ── Tree ────────────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<WarehouseLocationTreeDto> getTree() {
    List<WarehouseLocation> allActive = repository.findByIsActiveTrueOrderBySortOrderAscNameAsc();
    return buildTree(allActive);
  }

  @Transactional(readOnly = true)
  public WarehouseLocationTreeDto getSubTree(UUID rootId) {
    WarehouseLocation root = findEntityById(rootId);
    List<WarehouseLocation> descendants =
        root.getPath() != null ? repository.findDescendantsByPath(root.getPath()) : List.of(root);

    List<WarehouseLocationTreeDto> trees = buildTree(descendants);
    return trees.stream()
        .filter(t -> t.getId().equals(rootId))
        .findFirst()
        .orElse(WarehouseLocationTreeDto.from(root));
  }

  // ── Capacity ────────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public boolean checkCapacity(UUID locationId, BigDecimal weight, BigDecimal volume) {
    WarehouseLocation location = findEntityById(locationId);
    return location.hasCapacity(weight, volume);
  }

  // ── Private helpers ─────────────────────────────────────────────────────────

  private WarehouseLocation findEntityById(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Warehouse location not found: " + id));
  }

  private void checkVersion(
      String entityType, UUID entityId, Long clientVersion, Long currentVersion) {
    if (clientVersion != null && !clientVersion.equals(currentVersion)) {
      throw new OptimisticLockConflictException(
          entityType, entityId, clientVersion, currentVersion);
    }
  }

  private void validateCapacityFloor(
      WarehouseLocation location, BigDecimal newMaxWeight, BigDecimal newMaxVolume) {
    BigDecimal cw =
        location.getCurrentWeightKg() != null ? location.getCurrentWeightKg() : BigDecimal.ZERO;
    if (newMaxWeight != null && cw.compareTo(newMaxWeight) > 0) {
      throw new IwmDomainException(
          String.format(
              "Cannot set max weight to %.3f kg — current load is %.3f kg. Remove inventory first.",
              newMaxWeight, cw));
    }
    BigDecimal cv =
        location.getCurrentVolumeM3() != null ? location.getCurrentVolumeM3() : BigDecimal.ZERO;
    if (newMaxVolume != null && cv.compareTo(newMaxVolume) > 0) {
      throw new IwmDomainException(
          String.format(
              "Cannot set max volume to %.3f m³ — current volume is %.3f m³. Remove inventory first.",
              newMaxVolume, cv));
    }
  }

  private void validateCodeUnique(UUID tenantId, String code) {
    if (repository.existsByTenantIdAndCode(tenantId, code)) {
      throw new IwmDomainException("A warehouse location with code '" + code + "' already exists.");
    }
  }

  private void validateBarcodeUnique(UUID tenantId, String barcode) {
    if (repository.existsByTenantIdAndBarcode(tenantId, barcode)) {
      throw new IwmDomainException(
          "A warehouse location with barcode '" + barcode + "' already exists.");
    }
  }

  private void validateHierarchy(WarehouseLocationType childType, WarehouseLocation parent) {
    Set<WarehouseLocationType> allowed = ALLOWED_PARENTS.get(childType);
    if (allowed == null) {
      throw new IwmDomainException("Unknown location type: " + childType);
    }

    if (allowed.isEmpty()) {
      if (parent != null) {
        throw new IwmDomainException(
            childType
                + " must be a root location (no parent). Cannot be placed under "
                + parent.getType()
                + ".");
      }
      return;
    }

    if (parent == null) {
      throw new IwmDomainException(childType + " requires a parent location of type: " + allowed);
    }

    if (!allowed.contains(parent.getType())) {
      throw new IwmDomainException(
          String.format(
              "%s cannot be placed under %s. Allowed parent types: %s",
              childType, parent.getType(), allowed));
    }
  }

  private String buildPath(WarehouseLocation parent, String code) {
    if (parent == null || parent.getPath() == null) {
      return "/" + code;
    }
    return parent.getPath() + "/" + code;
  }

  private List<WarehouseLocationTreeDto> buildTree(List<WarehouseLocation> locations) {
    Map<UUID, WarehouseLocationTreeDto> nodeMap = new LinkedHashMap<>();
    List<WarehouseLocationTreeDto> roots = new ArrayList<>();

    for (WarehouseLocation loc : locations) {
      nodeMap.put(loc.getId(), WarehouseLocationTreeDto.from(loc));
    }

    for (WarehouseLocation loc : locations) {
      WarehouseLocationTreeDto node = nodeMap.get(loc.getId());
      if (loc.getParentId() == null || !nodeMap.containsKey(loc.getParentId())) {
        roots.add(node);
      } else {
        WarehouseLocationTreeDto parentNode = nodeMap.get(loc.getParentId());
        parentNode.getChildren().add(node);
      }
    }

    for (WarehouseLocationTreeDto node : nodeMap.values()) {
      node.setChildCount(node.getChildren().size());
      node.getChildren()
          .sort(
              Comparator.comparingInt(
                      (WarehouseLocationTreeDto n) ->
                          n.getSortOrder() != null ? n.getSortOrder() : 0)
                  .thenComparing(WarehouseLocationTreeDto::getName));
    }

    roots.sort(
        Comparator.comparingInt(
                (WarehouseLocationTreeDto n) -> n.getSortOrder() != null ? n.getSortOrder() : 0)
            .thenComparing(WarehouseLocationTreeDto::getName));

    return roots;
  }
}
