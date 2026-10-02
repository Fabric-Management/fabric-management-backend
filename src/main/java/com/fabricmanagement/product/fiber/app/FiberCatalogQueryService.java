package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.product.core.api.facade.ProductFacade;
import com.fabricmanagement.product.core.dto.ProductAttributeDto;
import com.fabricmanagement.product.fiber.dto.FiberCatalogSummaryDto;
import com.fabricmanagement.product.fiber.dto.FiberCategoryDto;
import com.fabricmanagement.product.fiber.dto.FiberCertificationDto;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.product.fiber.dto.FiberIsoCodeDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot catalogue load for the UI (FIBER-CATALOG-1): the shared reference catalogue (one row per
 * code, owned by the catalogue owner), product attributes, and every fibre the tenant can see (its
 * own plus the shared canonical fibres).
 *
 * <p>Exists to break the circular dependency between FiberService and ProductService.
 */
@Service
@RequiredArgsConstructor
public class FiberCatalogQueryService {

  private final FiberService fiberService;
  private final ProductFacade productFacade;
  private final FiberReferenceQueryService referenceQueryService;

  @Transactional(readOnly = true)
  public FiberCatalogSummaryDto getCatalogSummary() {
    List<FiberCategoryDto> categories = referenceQueryService.listCategories();
    List<FiberIsoCodeDto> isoCodes = referenceQueryService.listIsoCodes(false);
    List<FiberCertificationDto> certifications = referenceQueryService.listCertificationSchemes();
    List<ProductAttributeDto> attributes = productFacade.getAttributes("FIBER");
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
