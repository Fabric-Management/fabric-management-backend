package com.fabricmanagement.product.fabric.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.reference.FabricConstruction;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import com.fabricmanagement.product.fabric.dto.CreateFabricConstructionRequest;
import com.fabricmanagement.product.fabric.dto.FabricConstructionDto;
import com.fabricmanagement.product.fabric.dto.UpdateFabricConstructionRequest;
import com.fabricmanagement.product.fabric.infra.repository.FabricConstructionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The tenant's fabric construction dictionary (FABRIC-ARCHITECTURE F3, §2.6). */
@Service
@RequiredArgsConstructor
public class FabricConstructionCatalogService {

  static final Comparator<FabricConstruction> ORDER =
      Comparator.comparing(FabricConstruction::getFormation)
          .thenComparing(
              FabricConstruction::getDisplayOrder,
              Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
          .thenComparing(FabricConstruction::getName, String.CASE_INSENSITIVE_ORDER);

  private final FabricConstructionRepository repository;

  /** Constructions of the tenant, optionally of one formation, in usable-first display order. */
  @Transactional(readOnly = true)
  public List<FabricConstructionDto> list(FabricFormation formation, boolean includeInactive) {
    UUID tenantId = TenantContext.requireTenantId();
    return repository.findByTenantIdAndDeletedAtIsNull(tenantId).stream()
        .filter(construction -> includeInactive || construction.isUsable())
        .filter(construction -> formation == null || construction.getFormation() == formation)
        .sorted(ORDER)
        .map(FabricConstructionDto::from)
        .toList();
  }

  @Transactional
  public FabricConstructionDto create(CreateFabricConstructionRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    if (repository.existsByTenantIdAndCode(tenantId, request.code())) {
      throw new FabricDomainException(
          "A fabric construction with code " + request.code() + " already exists");
    }
    FabricConstruction construction =
        FabricConstruction.defineTenant(
            tenantId,
            request.code(),
            request.name(),
            request.description(),
            request.displayOrder(),
            request.family());
    return FabricConstructionDto.from(repository.save(construction));
  }

  @Transactional
  public FabricConstructionDto update(UUID id, UpdateFabricConstructionRequest request) {
    FabricConstruction construction = require(id);
    construction.update(
        request.name(), request.description(), request.displayOrder(), request.active());
    return FabricConstructionDto.from(repository.save(construction));
  }

  /** A construction of the current tenant, active or not; unknown ids are a 404. */
  @Transactional(readOnly = true)
  public FabricConstruction require(UUID id) {
    return repository
        .findByTenantIdAndIdAndDeletedAtIsNull(TenantContext.requireTenantId(), id)
        .orElseThrow(() -> new NotFoundException("Fabric construction not found: " + id));
  }
}
