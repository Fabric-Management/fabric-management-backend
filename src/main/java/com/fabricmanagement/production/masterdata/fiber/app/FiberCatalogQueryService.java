package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberCatalogSummaryDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberCategoryDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberCertificationDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberIsoCodeDto;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCategoryRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCertificationRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberIsoCodeRepository;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.dto.ProductAttributeDto;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application-layer query service that aggregates fiber catalog data from multiple domains.
 *
 * <p>This service exists to break the circular dependency between FiberService and ProductService.
 * FiberService provides core fiber CRUD; ProductFacade provides cross-module product attributes.
 * Neither should depend on the other — this orchestrator composes both into a single catalog
 * response for the UI.
 */
@Service
@RequiredArgsConstructor
public class FiberCatalogQueryService {

  private final FiberService fiberService;
  private final ProductFacade productFacade;
  private final FiberCategoryRepository fiberCategoryRepository;
  private final FiberIsoCodeRepository fiberIsoCodeRepository;
  private final FiberCertificationRepository fiberCertificationRepository;

  /**
   * Catalog summary: reference data + all fibers (tenant + platform seed) for one-shot UI load.
   *
   * <p>Reference data (categories, ISO codes, certifications) is queried with explicit tenant_id
   * filter to return only the tenant's own cloned copies. RLS carve-out makes template rows visible
   * for FK resolution (shared fibers reference template's reference data), but we don't want double
   * rows in the listing (8 cloned + 8 template = 16 would be wrong).
   *
   * <p>Fibers use tenantScope (own + template) because shared canonical fibers are NOT cloned.
   */
  @Transactional(readOnly = true)
  public FiberCatalogSummaryDto getCatalogSummary() {
    UUID tenantId = TenantContext.requireTenantId();

    List<FiberCategoryDto> categories =
        fiberCategoryRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
            .map(FiberCategoryDto::from)
            .toList();
    List<FiberIsoCodeDto> isoCodes =
        fiberIsoCodeRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
            .map(FiberIsoCodeDto::from)
            .toList();
    List<ProductAttributeDto> attributes = productFacade.getAttributes("FIBER");
    List<FiberCertificationDto> certifications =
        fiberCertificationRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
            .map(FiberCertificationDto::from)
            .toList();
    List<FiberDto> fibers = fiberService.getAll();
    return FiberCatalogSummaryDto.builder()
        .categories(categories)
        .isoCodes(isoCodes)
        .attributes(attributes)
        .certifications(certifications)
        .fibers(fibers)
        .build();
  }
}
