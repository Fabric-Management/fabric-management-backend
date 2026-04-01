package com.fabricmanagement.production.masterdata.qualitygrade.infra.repository;

import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.qualitygrade.domain.QualityGrade;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Tenant-scoped persistence for {@link QualityGrade}. */
@Repository
public interface QualityGradeRepository extends JpaRepository<QualityGrade, UUID> {

  List<QualityGrade> findByTenantIdAndMaterialTypeAndIsActiveTrue(
      UUID tenantId, MaterialType materialType);

  Optional<QualityGrade> findByTenantIdAndMaterialTypeAndCodeAndIsActiveTrue(
      UUID tenantId, MaterialType materialType, String code);

  boolean existsByTenantIdAndMaterialTypeAndCode(
      UUID tenantId, MaterialType materialType, String code);

  /** Used during onboarding to check if seed data already exists. */
  boolean existsByTenantIdAndMaterialType(UUID tenantId, MaterialType materialType);
}
