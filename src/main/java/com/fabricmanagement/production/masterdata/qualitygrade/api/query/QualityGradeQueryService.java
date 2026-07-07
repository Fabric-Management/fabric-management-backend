package com.fabricmanagement.production.masterdata.qualitygrade.api.query;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.qualitygrade.domain.QualityGrade;
import com.fabricmanagement.production.masterdata.qualitygrade.infra.repository.QualityGradeRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
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

  public record QualityGradeReference(
      UUID id, String code, String name, BigDecimal priceFactor, boolean saleable, boolean active) {
    static QualityGradeReference from(QualityGrade grade) {
      return new QualityGradeReference(
          grade.getId(),
          grade.getCode(),
          grade.getName(),
          grade.getPriceFactor(),
          grade.isSaleable(),
          Boolean.TRUE.equals(grade.getIsActive()));
    }
  }
}
