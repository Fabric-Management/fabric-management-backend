package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberComposition;
import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.fiber.dto.CreateFiberQualityStandardRequest;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.product.fiber.dto.FiberCatalogReferenceDto;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.product.fiber.infra.repository.FiberQualityStandardRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstrap-only fibre operations for the playground fibre fixtures (FIBER-CATALOG-1 §9).
 *
 * <p>Not exposed over HTTP. The caller (production's playground contributor) enforces the full
 * initial-provisioning eligibility guard before calling; these methods additionally refuse the
 * catalogue owner and TEMPLATE tenants, enforce every normal data invariant and are idempotent by
 * natural key, so a retried provisioning never duplicates a variant, blend or profile. Private
 * variants bypass only the human approval queue, never its rules. Shared materials are found by
 * exact ISO code; a missing shared seed fails with a diagnostic instead of a substitute.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberPlaygroundFixtureService {

  private static final Set<String> FIXTURE_TENANT_TYPES = Set.of("PLAYGROUND", "REGULAR");

  private final FiberService fiberService;
  private final FiberSourceVariantService sourceVariantService;
  private final FiberReferenceQueryService referenceQueryService;
  private final FiberQualityStandardService qualityStandardService;
  private final FiberQualityStandardRepository qualityStandardRepository;
  private final FiberRepository fiberRepository;
  private final TenantQueryPort tenantQueryPort;

  /** The canonical shared pure fibre of an ISO code; absence is a catalogue seed defect. */
  @Transactional(readOnly = true)
  public FiberCatalogReferenceDto requireSharedPure(String isoCode) {
    return fiberService
        .findCanonicalByIsoCode(isoCode)
        .orElseThrow(() -> missingSeed("canonical pure fibre for ISO " + isoCode));
  }

  /** Finds or creates the tenant's private pure variant of a shared ISO code. */
  @Transactional
  public Fiber ensureSourceVariant(String isoCode, MaterialSource source, String fiberName) {
    UUID tenantId = requireFixtureTenant();
    FiberIsoCode sharedIso =
        referenceQueryService
            .findIsoCode(isoCode)
            .orElseThrow(() -> missingSeed("shared ISO code " + isoCode));
    requireSharedPure(isoCode);
    Optional<Fiber> existing =
        sourceVariantService.findActiveVariant(tenantId, sharedIso.getId(), source);
    if (existing.isPresent()) {
      return existing.get();
    }
    FiberCategory category =
        referenceQueryService
            .findCategory(sharedIso.getFiberType())
            .orElseThrow(() -> missingSeed("shared category " + sharedIso.getFiberType()));
    Fiber created =
        sourceVariantService.createPrivateVariant(
            tenantId,
            new FiberSourceVariantService.SharedPureReference(sharedIso, category),
            fiberName,
            source);
    log.info(
        "Playground bootstrap created private variant: tenant={}, iso={}, source={}, actor={}",
        tenantId,
        sharedIso.getIsoCode(),
        source,
        TenantContext.getCurrentUserId());
    return created;
  }

  /** Finds or creates the tenant's blend with exactly this composition. */
  @Transactional
  public FiberDto ensureBlend(Map<UUID, BigDecimal> composition, String fiberName) {
    UUID tenantId = requireFixtureTenant();
    Optional<UUID> existingProduct = fiberService.findOwnBlendProductId(composition);
    if (existingProduct.isPresent()) {
      return fiberService
          .getByProductId(existingProduct.get())
          .orElseThrow(() -> new IllegalStateException("Blend product without fibre"));
    }
    return fiberService.createFiber(
        CreateFiberRequest.builder()
            .unit("KG")
            .fiberName(fiberName)
            .composition(FiberComposition.normalize(composition))
            .remarks("Playground example blend: illustrative demo data.")
            .build());
  }

  /**
   * Finds or creates a synthetic demo default profile with moisture-only criteria. The numbers
   * exercise software branches only; they are not material tolerances.
   */
  @Transactional
  public UUID ensureDemoMoistureProfile(
      FiberQualityTargetType targetType,
      UUID targetId,
      String standardName,
      double min,
      double target,
      double max) {
    UUID tenantId = requireFixtureTenant();
    Optional<FiberQualityStandard> existing =
        (targetType == FiberQualityTargetType.ISO_CODE
                ? qualityStandardRepository.findByTenantIdAndTargetTypeAndIsoCode_IdAndIsActiveTrue(
                    tenantId, targetType, targetId)
                : qualityStandardRepository.findByTenantIdAndTargetTypeAndFiberIdAndIsActiveTrue(
                    tenantId, targetType, targetId))
            .stream().filter(profile -> standardName.equals(profile.getStandardName())).findFirst();
    if (existing.isPresent()) {
      return existing.get().getId();
    }
    return qualityStandardService
        .create(
            CreateFiberQualityStandardRequest.builder()
                .targetType(targetType)
                .isoCodeId(targetType == FiberQualityTargetType.ISO_CODE ? targetId : null)
                .fiberId(targetType == FiberQualityTargetType.FIBER ? targetId : null)
                .standardName(standardName)
                .isDefault(true)
                .moisturePctMin(min)
                .moisturePctTarget(target)
                .moisturePctMax(max)
                .build())
        .getId();
  }

  /** Active own fibre by id (fixture ledger repair check). */
  @Transactional(readOnly = true)
  public boolean ownFiberExists(UUID fiberId) {
    UUID tenantId = TenantContext.requireTenantId();
    return fiberRepository
        .findByTenantIdAndId(tenantId, fiberId)
        .filter(fiber -> Boolean.TRUE.equals(fiber.getIsActive()))
        .isPresent();
  }

  private UUID requireFixtureTenant() {
    UUID tenantId = TenantContext.requireTenantId();
    String type =
        tenantQueryPort
            .findById(tenantId)
            .map(tenant -> tenant.type() == null ? "" : tenant.type().toUpperCase(Locale.ROOT))
            .orElse("");
    if (FiberCatalog.isOwner(tenantId) || !FIXTURE_TENANT_TYPES.contains(type)) {
      throw new IllegalStateException(
          "Playground fibre fixtures are never installed for tenant "
              + tenantId
              + " ("
              + type
              + ")");
    }
    return tenantId;
  }

  private static FiberDomainException missingSeed(String what) {
    return new FiberDomainException(
        "Playground fixtures need the shared catalogue's " + what + "; publish the catalogue seed",
        "PLAYGROUND_CATALOGUE_SEED_MISSING",
        500,
        new Object[] {what});
  }
}
