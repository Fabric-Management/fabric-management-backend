package com.fabricmanagement.product.fabric.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.reference.FabricFinishingProcess;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.dto.CreateFabricFinishingProcessRequest;
import com.fabricmanagement.product.fabric.dto.FabricFinishingProcessDto;
import com.fabricmanagement.product.fabric.dto.UpdateFabricFinishingProcessRequest;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingProcessRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The tenant's finishing process dictionary (FABRIC-3a). */
@Service
@RequiredArgsConstructor
public class FabricFinishingCatalogService {

  static final Comparator<FabricFinishingProcess> ORDER =
      Comparator.comparing(FabricFinishingProcess::getCategory)
          .thenComparing(
              FabricFinishingProcess::getDisplayOrder,
              Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
          .thenComparing(FabricFinishingProcess::getName, String.CASE_INSENSITIVE_ORDER);

  private final FabricFinishingProcessRepository repository;

  /** The tenant's processes, optionally of one category, in category then display order. */
  @Transactional(readOnly = true)
  public List<FabricFinishingProcessDto> list(
      FabricFinishingCategory category, boolean includeInactive) {
    UUID tenantId = TenantContext.requireTenantId();
    return repository.findByTenantIdAndDeletedAtIsNull(tenantId).stream()
        .filter(process -> includeInactive || process.isUsable())
        .filter(process -> category == null || process.getCategory() == category)
        .sorted(ORDER)
        .map(FabricFinishingProcessDto::from)
        .toList();
  }

  @Transactional
  public FabricFinishingProcessDto create(CreateFabricFinishingProcessRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    if (repository.existsByTenantIdAndCode(tenantId, request.code())) {
      throw new FabricDomainException(
          "A finishing process with code " + request.code() + " already exists");
    }
    FabricFinishingProcess process =
        FabricFinishingProcess.defineTenant(
            tenantId,
            request.code(),
            request.name(),
            request.description(),
            request.displayOrder(),
            request.category(),
            request.chargeUnit());
    return FabricFinishingProcessDto.from(repository.save(process));
  }

  @Transactional
  public FabricFinishingProcessDto update(UUID id, UpdateFabricFinishingProcessRequest request) {
    FabricFinishingProcess process = require(id);
    process.update(
        request.name(),
        request.description(),
        request.displayOrder(),
        request.chargeUnit(),
        request.active());
    return FabricFinishingProcessDto.from(repository.save(process));
  }

  /** A process of the current tenant, active or not; unknown ids are a 404. */
  @Transactional(readOnly = true)
  public FabricFinishingProcess require(UUID id) {
    return repository
        .findByTenantIdAndIdAndDeletedAtIsNull(TenantContext.requireTenantId(), id)
        .orElseThrow(() -> new NotFoundException("Finishing process not found: " + id));
  }
}
