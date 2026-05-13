package com.fabricmanagement.production.masterdata.qualitygrade.infra.repository;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.qualitygrade.domain.QualityGrade;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Tenant-scoped persistence for {@link QualityGrade}. */
@Repository
public interface QualityGradeRepository extends JpaRepository<QualityGrade, UUID> {

  List<QualityGrade> findByTenantIdAndProductTypeAndIsActiveTrue(
      UUID tenantId, ProductType productType);

  Optional<QualityGrade> findByTenantIdAndProductTypeAndCodeAndIsActiveTrue(
      UUID tenantId, ProductType productType, String code);

  boolean existsByTenantIdAndProductTypeAndCode(
      UUID tenantId, ProductType productType, String code);

  /** Used during onboarding to check if seed data already exists. */
  boolean existsByTenantIdAndProductType(UUID tenantId, ProductType productType);
}
