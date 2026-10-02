package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberComposition;
import com.fabricmanagement.product.fiber.domain.FiberKind;
import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.infra.repository.FiberQualityStandardRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read boundary for production consumers of fibre quality data (FIBER-CATALOG-1).
 *
 * <p>One deterministic resolver serves batch creation, the read-only applicability query and QC
 * evaluation: an explicit profile must apply; otherwise the exact FIBER default, then (only for a
 * pure fibre at exactly 100%) the tenant's ISO default; otherwise nothing. No dominant-component
 * fallback, no weighted average, no first-found or borrowed profile. The product module never
 * depends on production: callers pass IDs and composition maps.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FiberQualityQueryService {

  private final FiberRepository fiberRepository;
  private final FiberQualityStandardRepository qualityStandardRepository;
  private final FiberValidationService validationService;

  /** The fibre behind a batch product and its validated, normalised effective composition. */
  public record EffectiveComposition(
      UUID fiberId,
      UUID fiberIsoCodeId,
      FiberKind kind,
      String fiberName,
      Map<UUID, BigDecimal> composition) {}

  /** Where a resolved profile came from. */
  public enum ProfileSource {
    EXPLICIT,
    EXACT_FIBER_DEFAULT,
    ISO_DEFAULT,
    NONE
  }

  public record QualityResolution(FiberQualityStandard profile, ProfileSource source) {
    public Optional<FiberQualityStandard> profileOptional() {
      return Optional.ofNullable(profile);
    }
  }

  /** Outcome of re-evaluating a stored batch; an empty standard keeps the manual path. */
  public record StoredEvaluation(
      FiberQualityStandard standard, String targetLabel, String diagnosticCode) {
    public Optional<FiberQualityStandard> standardOptional() {
      return Optional.ofNullable(standard);
    }
  }

  /** Tenant-scoped fibre lookup by product: the tenant's own rows plus the shared catalogue. */
  public Optional<Fiber> findByProductId(UUID tenantId, UUID productId) {
    return fiberRepository.findInScopeByProductId(FiberCatalog.readScope(tenantId), productId);
  }

  public Optional<FiberQualityStandard> findQualityStandardById(
      UUID tenantId, UUID qualityStandardId) {
    return qualityStandardRepository.findByTenantIdAndId(tenantId, qualityStandardId);
  }

  /**
   * Validates a new batch's effective composition. {@code override == null} uses the fibre
   * definition ({@code {fiber: 100}} for a pure fibre); an explicit empty override is invalid. Both
   * paths go through the same validation (one pure component at exactly 100% is allowed), so a
   * definition whose component was later deactivated is rejected exactly like the same composition
   * sent explicitly. Existing batches keep their stored snapshot and are never re-validated here.
   */
  public EffectiveComposition resolveEffectiveComposition(
      UUID tenantId, UUID productId, Map<UUID, BigDecimal> override) {
    Fiber fiber =
        findByProductId(tenantId, productId)
            .orElseThrow(
                () ->
                    new FiberDomainException(
                        "No fibre is defined for this product",
                        "FIBER_PRODUCT_NOT_FOUND",
                        404,
                        new Object[] {productId}));
    Map<UUID, BigDecimal> requested =
        override != null
            ? override
            : fiber.isPure() ? FiberComposition.pure(fiber.getId()) : fiber.getComposition();
    Map<UUID, BigDecimal> effective =
        validationService.validateEffectiveComposition(requested, tenantId).composition();
    return new EffectiveComposition(
        fiber.getId(),
        fiber.getFiberIsoCodeId(),
        fiber.getKind(),
        fiber.getFiberName(),
        FiberComposition.normalize(effective));
  }

  /**
   * The fibre definition's composition for a product, or empty when the product has no fibre
   * definition (such a batch has unknown composition and stays on the manual QC path).
   */
  public Optional<EffectiveComposition> definitionComposition(UUID tenantId, UUID productId) {
    return findByProductId(tenantId, productId)
        .map(
            fiber ->
                new EffectiveComposition(
                    fiber.getId(),
                    fiber.getFiberIsoCodeId(),
                    fiber.getKind(),
                    fiber.getFiberName(),
                    fiber.isPure()
                        ? FiberComposition.pure(fiber.getId())
                        : FiberComposition.normalize(fiber.getComposition())));
  }

  /**
   * Batch-creation resolution. An explicit profile must be the tenant's active profile (404
   * otherwise) and must apply to this exact input (409 {@code FIBER_QUALITY_TARGET_MISMATCH}).
   */
  public QualityResolution resolve(
      UUID tenantId, EffectiveComposition effective, UUID explicitProfileId) {
    if (explicitProfileId != null) {
      FiberQualityStandard explicit =
          qualityStandardRepository
              .findByTenantIdAndIdAndIsActiveTrue(tenantId, explicitProfileId)
              .orElseThrow(
                  () ->
                      new FiberDomainException(
                          "Quality standard not found",
                          "FIBER_QUALITY_STANDARD_NOT_FOUND",
                          404,
                          new Object[] {explicitProfileId}));
      if (!applies(explicit, effective)) {
        throw new FiberDomainException(
            "The selected quality profile does not apply to this fibre and composition",
            "FIBER_QUALITY_TARGET_MISMATCH",
            409,
            new Object[] {explicitProfileId});
      }
      return new QualityResolution(explicit, ProfileSource.EXPLICIT);
    }
    return resolveDefault(tenantId, effective);
  }

  /** Default resolution only (no explicit choice). */
  public QualityResolution resolveDefault(UUID tenantId, EffectiveComposition effective) {
    Optional<FiberQualityStandard> exact =
        qualityStandardRepository
            .findByTenantIdAndTargetTypeAndFiberIdAndIsDefaultTrueAndIsActiveTrue(
                tenantId, FiberQualityTargetType.FIBER, effective.fiberId())
            .filter(profile -> applies(profile, effective));
    if (exact.isPresent()) {
      return new QualityResolution(exact.get(), ProfileSource.EXACT_FIBER_DEFAULT);
    }
    if (effective.kind() == FiberKind.PURE
        && FiberComposition.sameComposition(
            effective.composition(), FiberComposition.pure(effective.fiberId()))) {
      Optional<FiberQualityStandard> iso =
          qualityStandardRepository
              .findByTenantIdAndTargetTypeAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(
                  tenantId, FiberQualityTargetType.ISO_CODE, effective.fiberIsoCodeId())
              .filter(profile -> applies(profile, effective));
      if (iso.isPresent()) {
        return new QualityResolution(iso.get(), ProfileSource.ISO_DEFAULT);
      }
    }
    return new QualityResolution(null, ProfileSource.NONE);
  }

  /** Every active tenant profile that applies to this exact input. */
  public List<FiberQualityStandard> applicableProfiles(
      UUID tenantId, EffectiveComposition effective) {
    List<FiberQualityStandard> candidates =
        new ArrayList<>(
            qualityStandardRepository.findByTenantIdAndTargetTypeAndFiberIdAndIsActiveTrue(
                tenantId, FiberQualityTargetType.FIBER, effective.fiberId()));
    if (effective.fiberIsoCodeId() != null) {
      candidates.addAll(
          qualityStandardRepository.findByTenantIdAndTargetTypeAndIsoCode_IdAndIsActiveTrue(
              tenantId, FiberQualityTargetType.ISO_CODE, effective.fiberIsoCodeId()));
    }
    return candidates.stream().filter(profile -> applies(profile, effective)).toList();
  }

  /**
   * QC re-evaluation against a batch's stored composition snapshot. A stored explicit profile is
   * re-checked and never replaced by another one; an unknown snapshot is never treated as a pure
   * composition and never replaced by the current catalogue definition.
   *
   * @param snapshot stored composition, or empty when it is missing or malformed
   */
  public StoredEvaluation evaluateStored(
      UUID tenantId,
      UUID productId,
      Optional<Map<UUID, BigDecimal>> snapshot,
      UUID storedProfileId) {
    Optional<Fiber> fiber = findByProductId(tenantId, productId);
    if (fiber.isEmpty()) {
      return new StoredEvaluation(null, null, "FIBER_PRODUCT_NOT_FOUND");
    }
    String label = fiber.get().getFiberName();
    if (snapshot.isEmpty() || snapshot.get().isEmpty()) {
      return new StoredEvaluation(null, label, "BATCH_COMPOSITION_UNKNOWN");
    }
    EffectiveComposition effective =
        new EffectiveComposition(
            fiber.get().getId(),
            fiber.get().getFiberIsoCodeId(),
            fiber.get().getKind(),
            label,
            FiberComposition.normalize(snapshot.get()));
    if (storedProfileId != null) {
      Optional<FiberQualityStandard> stored =
          qualityStandardRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, storedProfileId);
      if (stored.isEmpty()) {
        return new StoredEvaluation(null, label, "QUALITY_PROFILE_UNAVAILABLE");
      }
      if (!applies(stored.get(), effective)) {
        return new StoredEvaluation(null, label, "QUALITY_PROFILE_NOT_APPLICABLE");
      }
      return new StoredEvaluation(stored.get(), label, null);
    }
    QualityResolution resolution = resolveDefault(tenantId, effective);
    return resolution.profile() == null
        ? new StoredEvaluation(null, label, "NO_APPLICABLE_QUALITY_PROFILE")
        : new StoredEvaluation(resolution.profile(), label, null);
  }

  private static boolean applies(FiberQualityStandard profile, EffectiveComposition effective) {
    return profile.appliesTo(
        effective.fiberId(), effective.fiberIsoCodeId(), effective.kind(), effective.composition());
  }
}
