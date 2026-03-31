package com.fabricmanagement.production.masterdata.qualitygrade.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.qualitygrade.domain.QualityGrade;
import com.fabricmanagement.production.masterdata.qualitygrade.infra.repository.QualityGradeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages tenant-specific quality grade definitions.
 *
 * <h2>Seed Data</h2>
 *
 * <p>On tenant creation, {@link #seedDefaultGrades(UUID)} is called to bootstrap 5 default grades
 * per material type. Seed is idempotent — safe to call multiple times.
 *
 * <h2>Grade Transition Enforcement</h2>
 *
 * <p>This service does <b>not</b> enforce the approval requirement for grade upgrades because that
 * is a StockUnit operation, not a QualityGrade management operation. Grade approval logic lives in
 * {@code StockUnitService.changeGrade()}, which fetches both the old and new grade and asks {@code
 * QualityGrade.requiresApprovalForTransition(newGrade)} before proceeding.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityGradeService {

  private final QualityGradeRepository qualityGradeRepository;

  // ── Queries ───────────────────────────────────────────────────────────────

  /**
   * Returns all active grades for the current tenant and the given material type. Ordered by rank
   * ascending (best quality first).
   */
  @Transactional(readOnly = true)
  public List<QualityGrade> findByMaterialType(MaterialType materialType) {
    UUID tenantId = TenantContext.requireTenantId();
    return qualityGradeRepository
        .findByTenantIdAndMaterialTypeAndIsActiveTrue(tenantId, materialType)
        .stream()
        .sorted(java.util.Comparator.comparingInt(QualityGrade::getRank))
        .toList();
  }

  /**
   * Returns a grade by its unique code within the current tenant + material type.
   *
   * @throws com.fabricmanagement.production.common.exception.ProductionDomainException if not found
   */
  @Transactional(readOnly = true)
  public QualityGrade findByCode(MaterialType materialType, String code) {
    UUID tenantId = TenantContext.requireTenantId();
    return qualityGradeRepository
        .findByTenantIdAndMaterialTypeAndCodeAndIsActiveTrue(tenantId, materialType, code)
        .orElseThrow(
            () ->
                new com.fabricmanagement.production.execution.stockunit.domain.exception
                    .StockUnitDomainException(
                    String.format(
                        "QualityGrade not found: materialType=%s, code=%s", materialType, code)));
  }

  /**
   * Returns a grade by ID, validating tenant ownership.
   *
   * @throws com.fabricmanagement.production.common.exception.ProductionDomainException if not found
   *     or if the grade belongs to a different tenant
   */
  @Transactional(readOnly = true)
  public QualityGrade findById(UUID gradeId) {
    UUID tenantId = TenantContext.requireTenantId();
    return qualityGradeRepository
        .findById(gradeId)
        .filter(g -> g.getTenantId().equals(tenantId))
        .orElseThrow(
            () ->
                new com.fabricmanagement.production.execution.stockunit.domain.exception
                    .StockUnitDomainException("QualityGrade not found: " + gradeId));
  }

  // ── Commands ──────────────────────────────────────────────────────────────

  /** Creates a new grade for the current tenant. */
  @Transactional
  public QualityGrade create(
      MaterialType materialType,
      String code,
      String name,
      int rank,
      BigDecimal priceFactor,
      boolean saleable,
      boolean requiresApproval,
      String colorHex,
      boolean isDefault) {

    UUID tenantId = TenantContext.requireTenantId();

    if (qualityGradeRepository.existsByTenantIdAndMaterialTypeAndCode(
        tenantId, materialType, code.toUpperCase().trim())) {
      throw new com.fabricmanagement.production.execution.stockunit.domain.exception
          .StockUnitDomainException(
          String.format(
              "QualityGrade already exists: materialType=%s, code=%s", materialType, code));
    }

    QualityGrade grade =
        QualityGrade.create(
            tenantId,
            materialType,
            code,
            name,
            rank,
            priceFactor,
            saleable,
            requiresApproval,
            colorHex,
            isDefault);
    return qualityGradeRepository.save(grade);
  }

  /** Updates mutable fields of an existing grade. Code and materialType are immutable. */
  @Transactional
  public QualityGrade update(
      UUID gradeId,
      String name,
      int rank,
      BigDecimal priceFactor,
      boolean saleable,
      boolean requiresApproval,
      String colorHex,
      boolean isDefault) {

    QualityGrade grade = findById(gradeId);
    grade.update(name, rank, priceFactor, saleable, requiresApproval, colorHex, isDefault);
    return qualityGradeRepository.save(grade);
  }

  /** Soft-deletes a grade. Cannot delete the default grade if it is the last for its type. */
  @Transactional
  public void deactivate(UUID gradeId) {
    QualityGrade grade = findById(gradeId);
    grade.softDelete();
    qualityGradeRepository.save(grade);
    log.info("QualityGrade deactivated: id={}, code={}", gradeId, grade.getCode());
  }

  // ── Seed ─────────────────────────────────────────────────────────────────

  /**
   * Seeds default quality grades for a given tenant.
   *
   * <p>Called from {@code TenantCreatedEventListener}. Idempotent — skips material types that
   * already have at least one grade.
   *
   * <p>Default grades per material type:
   *
   * <pre>
   * Rank 1: 1A — "Birinci Kalite A"  — pf=1.000 — saleable — no approval — default
   * Rank 2: 1B — "Birinci Kalite B"  — pf=0.950 — saleable — no approval
   * Rank 3: 2  — "İkinci Kalite"     — pf=0.800 — saleable — no approval
   * Rank 4: OF — "Off-Quality"       — pf=0.400 — saleable — requires approval to upgrade
   * Rank 5: WT — "Atık/Fire"         — pf=0.000 — not saleable — requires approval to upgrade
   * </pre>
   *
   * @param tenantId the new tenant's ID
   */
  @Transactional
  public void seedDefaultGrades(UUID tenantId) {
    for (MaterialType materialType : MaterialType.values()) {
      if (qualityGradeRepository.existsByTenantIdAndMaterialType(tenantId, materialType)) {
        log.debug(
            "Seed skipped for tenantId={} materialType={} — grades already exist.",
            tenantId,
            materialType);
        continue;
      }
      persistSeedGrades(tenantId, materialType);
      log.info(
          "Seeded default QualityGrades for tenantId={} materialType={}", tenantId, materialType);
    }
  }

  private void persistSeedGrades(UUID tenantId, MaterialType materialType) {
    qualityGradeRepository.save(
        QualityGrade.create(
            tenantId,
            materialType,
            "1A",
            "Birinci Kalite A",
            1,
            new BigDecimal("1.000"),
            true,
            false,
            "#22C55E",
            true));

    qualityGradeRepository.save(
        QualityGrade.create(
            tenantId,
            materialType,
            "1B",
            "Birinci Kalite B",
            2,
            new BigDecimal("0.950"),
            true,
            false,
            "#84CC16",
            false));

    qualityGradeRepository.save(
        QualityGrade.create(
            tenantId,
            materialType,
            "2",
            "İkinci Kalite",
            3,
            new BigDecimal("0.800"),
            true,
            false,
            "#EAB308",
            false));

    qualityGradeRepository.save(
        QualityGrade.create(
            tenantId,
            materialType,
            "OF",
            "Off-Quality",
            4,
            new BigDecimal("0.400"),
            true,
            true,
            "#F97316",
            false));

    qualityGradeRepository.save(
        QualityGrade.create(
            tenantId,
            materialType,
            "WT",
            "Atık/Fire",
            5,
            new BigDecimal("0.000"),
            false,
            true,
            "#EF4444",
            false));
  }
}
