package com.fabricmanagement.production.masterdata.qualitygrade.api.query;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.qualitygrade.domain.QualityGrade;
import com.fabricmanagement.production.masterdata.qualitygrade.infra.repository.QualityGradeRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public query contract for other modules to read quality-grade references without coupling to
 * production controllers or repositories.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QualityGradeQueryService {

  private final QualityGradeRepository qualityGradeRepository;

  public List<QualityGradeReference> findSalesGradeReferences(
      ProductType productType, boolean includeNonSaleable) {
    UUID tenantId = TenantContext.requireTenantId();
    return qualityGradeRepository
        .findByTenantIdAndProductTypeAndIsActiveTrue(tenantId, productType)
        .stream()
        .filter(grade -> includeNonSaleable || grade.isSaleable())
        .sorted(Comparator.comparingInt(QualityGrade::getRank))
        .map(QualityGradeReference::from)
        .toList();
  }

  public List<QualityGradeReference> findSalesGradeReferences(
      String productType, boolean includeNonSaleable) {
    if (productType == null || productType.isBlank()) {
      throw new IllegalArgumentException("productType is required");
    }
    ProductType resolvedProductType;
    try {
      resolvedProductType = ProductType.valueOf(productType.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unknown productType: " + productType, ex);
    }
    return findSalesGradeReferences(resolvedProductType, includeNonSaleable);
  }

  public Optional<QualityGradeReference> findActiveReferenceById(UUID gradeId) {
    UUID tenantId = TenantContext.requireTenantId();
    return qualityGradeRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, gradeId)
        .map(QualityGradeReference::from);
  }

  public Optional<QualityGradeReference> findReferenceById(UUID gradeId) {
    UUID tenantId = TenantContext.requireTenantId();
    return qualityGradeRepository
        .findByTenantIdAndId(tenantId, gradeId)
        .map(QualityGradeReference::from);
  }

  public List<QualityGradeReference> findReferencesByIds(Collection<UUID> gradeIds) {
    if (gradeIds == null || gradeIds.isEmpty()) {
      return List.of();
    }
    UUID tenantId = TenantContext.requireTenantId();
    return qualityGradeRepository.findByTenantIdAndIdIn(tenantId, gradeIds).stream()
        .map(QualityGradeReference::from)
        .toList();
  }

  public record QualityGradeReference(
      UUID id,
      String code,
      String name,
      int rank,
      BigDecimal priceFactor,
      boolean saleable,
      boolean active) {
    static QualityGradeReference from(QualityGrade grade) {
      return new QualityGradeReference(
          grade.getId(),
          grade.getCode(),
          grade.getName(),
          grade.getRank(),
          grade.getPriceFactor(),
          grade.isSaleable(),
          Boolean.TRUE.equals(grade.getIsActive()));
    }
  }
}
