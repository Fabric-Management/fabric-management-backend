package com.fabricmanagement.product.fabric.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPriceClass;
import com.fabricmanagement.product.fabric.dto.CreateFabricFinishingPriceClassRequest;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceClassDto;
import com.fabricmanagement.product.fabric.dto.UpdateFabricFinishingPriceClassRequest;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingPriceClassRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The tenant's price classes, the tiers its subcontractors price a dyeing by (FABRIC-3c). */
@Service
@RequiredArgsConstructor
public class FabricFinishingPriceClassService {

  static final Comparator<FabricFinishingPriceClass> ORDER =
      Comparator.comparing(
              FabricFinishingPriceClass::getDisplayOrder,
              Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
          .thenComparing(FabricFinishingPriceClass::getName, String.CASE_INSENSITIVE_ORDER);

  private final FabricFinishingPriceClassRepository repository;

  @Transactional(readOnly = true)
  public List<FabricFinishingPriceClassDto> list(boolean includeInactive) {
    return repository.findByTenantIdAndDeletedAtIsNull(TenantContext.requireTenantId()).stream()
        .filter(priceClass -> includeInactive || priceClass.isUsable())
        .sorted(ORDER)
        .map(FabricFinishingPriceClassDto::from)
        .toList();
  }

  @Transactional
  public FabricFinishingPriceClassDto create(CreateFabricFinishingPriceClassRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    if (repository.existsByTenantIdAndCode(tenantId, request.code())) {
      throw new FabricDomainException(
          "A finishing price class with code " + request.code() + " already exists",
          "FINISHING_PRICE_CLASS_DUPLICATE",
          409);
    }
    return FabricFinishingPriceClassDto.from(
        repository.save(
            FabricFinishingPriceClass.defineTenant(
                tenantId,
                request.code(),
                request.name(),
                request.description(),
                request.displayOrder())));
  }

  @Transactional
  public FabricFinishingPriceClassDto update(
      UUID id, UpdateFabricFinishingPriceClassRequest request) {
    FabricFinishingPriceClass priceClass = require(id);
    priceClass.update(
        request.name(), request.description(), request.displayOrder(), request.active());
    return FabricFinishingPriceClassDto.from(repository.save(priceClass));
  }

  /** A class of the current tenant, active or not; unknown ids are a 404. */
  @Transactional(readOnly = true)
  public FabricFinishingPriceClass require(UUID id) {
    return repository
        .findByTenantIdAndIdAndDeletedAtIsNull(TenantContext.requireTenantId(), id)
        .orElseThrow(() -> new NotFoundException("Finishing price class not found: " + id));
  }
}
