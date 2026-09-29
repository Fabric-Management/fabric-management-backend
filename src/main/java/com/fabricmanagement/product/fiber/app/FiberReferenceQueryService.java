package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.fiber.dto.FiberCategoryDto;
import com.fabricmanagement.product.fiber.dto.FiberCertificationDto;
import com.fabricmanagement.product.fiber.dto.FiberIsoCodeDto;
import com.fabricmanagement.product.fiber.infra.repository.FiberCategoryRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberCertificationRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberIsoCodeRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared fibre reference catalogue (FIBER-CATALOG-1): categories, ISO codes and certification
 * schemes. Every read names the catalogue owner explicitly, so a tenant sees exactly one shared row
 * per code and never a tenant copy.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FiberReferenceQueryService {

  private final FiberCategoryRepository categoryRepository;
  private final FiberIsoCodeRepository isoCodeRepository;
  private final FiberCertificationRepository certificationRepository;

  /** Normalises an ISO code at a publication/request boundary: trim + {@code Locale.ROOT} upper. */
  public static String normalizeIsoCode(String isoCode) {
    return isoCode == null ? null : isoCode.trim().toUpperCase(Locale.ROOT);
  }

  public List<FiberCategoryDto> listCategories() {
    return categoryRepository
        .findByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(FiberCatalog.OWNER_ID)
        .stream()
        .map(FiberCategoryDto::from)
        .toList();
  }

  public List<FiberIsoCodeDto> listIsoCodes(boolean officialOnly) {
    List<FiberIsoCode> codes =
        officialOnly
            ? isoCodeRepository
                .findByTenantIdAndIsOfficialIsoTrueAndIsActiveTrueOrderByDisplayOrderAsc(
                    FiberCatalog.OWNER_ID)
            : isoCodeRepository.findByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(
                FiberCatalog.OWNER_ID);
    return codes.stream().map(FiberIsoCodeDto::from).toList();
  }

  public List<FiberCertificationDto> listCertificationSchemes() {
    return certificationRepository
        .findByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(FiberCatalog.OWNER_ID)
        .stream()
        .map(FiberCertificationDto::from)
        .toList();
  }

  public Optional<FiberIsoCode> findIsoCode(String isoCode) {
    return isoCodeRepository.findByTenantIdAndIsoCodeIgnoreCase(
        FiberCatalog.OWNER_ID, normalizeIsoCode(isoCode));
  }

  public Optional<FiberIsoCode> findIsoCodeById(UUID id) {
    return id == null
        ? Optional.empty()
        : isoCodeRepository.findByTenantIdAndId(FiberCatalog.OWNER_ID, id);
  }

  public Optional<FiberCategory> findCategory(String categoryCode) {
    return categoryCode == null
        ? Optional.empty()
        : categoryRepository.findByTenantIdAndCategoryCode(
            FiberCatalog.OWNER_ID, categoryCode.trim().toUpperCase(Locale.ROOT));
  }

  public Optional<FiberCategory> findCategoryById(UUID id) {
    return id == null
        ? Optional.empty()
        : categoryRepository.findByTenantIdAndId(FiberCatalog.OWNER_ID, id);
  }

  /** The shared MIXED_BLEND category every blend uses; its absence is a catalogue defect. */
  public FiberCategory requireMixedBlendCategory() {
    return categoryRepository
        .findByTenantIdAndCategoryCode(
            FiberCatalog.OWNER_ID, FiberCatalog.MIXED_BLEND_CATEGORY_CODE)
        .filter(category -> Boolean.TRUE.equals(category.getIsActive()))
        .orElseThrow(
            () ->
                new FiberDomainException(
                    "The shared catalogue does not publish the MIXED_BLEND category",
                    "FIBER_CATEGORY_MIXED_BLEND_MISSING",
                    500));
  }
}
