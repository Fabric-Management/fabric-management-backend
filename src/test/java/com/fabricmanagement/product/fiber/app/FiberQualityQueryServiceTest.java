package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService.EffectiveComposition;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService.ProfileSource;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService.QualityResolution;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService.StoredEvaluation;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberKind;
import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.fiber.infra.repository.FiberQualityStandardRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The single deterministic quality resolver (FIBER-CATALOG-1 §8): explicit must apply, then the
 * exact FIBER default, then — only for a pure fibre at exactly 100% — the ISO default; never a
 * dominant-component or first-found fallback (A11–A14 unit level; ITs cover persistence).
 */
@ExtendWith(MockitoExtension.class)
class FiberQualityQueryServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private FiberRepository fiberRepository;
  @Mock private FiberQualityStandardRepository standardRepository;
  @Mock private FiberValidationService validationService;

  private FiberQualityQueryService queryService;

  private FiberIsoCode cottonIso;
  private Fiber cotton;
  private UUID polyesterId;
  private Fiber blend;

  @BeforeEach
  void setUp() {
    queryService =
        new FiberQualityQueryService(fiberRepository, standardRepository, validationService);
    FiberCategory natural =
        FiberCategory.builder().categoryCode("NATURAL_PLANT").categoryName("Plant").build();
    natural.setId(UUID.randomUUID());
    cottonIso =
        FiberIsoCode.builder()
            .isoCode("CO")
            .fiberName("Cotton")
            .fiberType("NATURAL_PLANT")
            .isOfficialIso(true)
            .build();
    cottonIso.setId(UUID.randomUUID());
    cotton = Fiber.createCanonicalPure(product(), natural, cottonIso, "Cotton (100%)");
    cotton.setId(UUID.randomUUID());
    cotton.setTenantId(FiberCatalog.OWNER_ID);

    polyesterId = UUID.randomUUID();
    FiberCategory mixed =
        FiberCategory.builder()
            .categoryCode(FiberCatalog.MIXED_BLEND_CATEGORY_CODE)
            .categoryName("Mixed")
            .build();
    blend =
        Fiber.createBlend(
            product(),
            mixed,
            "CO 60% / PES 40%",
            Map.of(cotton.getId(), new BigDecimal("60"), polyesterId, new BigDecimal("40")));
    blend.setId(UUID.randomUUID());
    blend.setTenantId(TENANT_ID);
  }

  private static Product product() {
    Product product = Product.create(ProductType.FIBER, "KG");
    product.setId(UUID.randomUUID());
    return product;
  }

  private FiberQualityStandard isoDefault() {
    FiberQualityStandard profile = FiberQualityStandard.forIsoCode(cottonIso, "Cotton default");
    profile.setId(UUID.randomUUID());
    profile.setIsDefault(true);
    profile.setMoisturePctMax(8.0);
    return profile;
  }

  private FiberQualityStandard fiberProfile(Fiber target, String name) {
    FiberQualityStandard profile =
        FiberQualityStandard.forFiber(
            target.getId(),
            target.isPure()
                ? Map.of(target.getId(), new BigDecimal("100"))
                : target.getComposition(),
            name);
    profile.setId(UUID.randomUUID());
    profile.setMoisturePctMax(8.0);
    return profile;
  }

  private static EffectiveComposition effective(Fiber fiber, Map<UUID, BigDecimal> composition) {
    return new EffectiveComposition(
        fiber.getId(),
        fiber.getFiberIsoCodeId(),
        fiber.getKind(),
        fiber.getFiberName(),
        composition);
  }

  @Test
  void readsFibresThroughTheSharedScope() {
    UUID productId = cotton.getProductId();
    when(fiberRepository.findInScopeByProductId(FiberCatalog.readScope(TENANT_ID), productId))
        .thenReturn(Optional.of(cotton));

    assertThat(queryService.findByProductId(TENANT_ID, productId)).contains(cotton);
  }

  @Test
  void pureFibreAtOneHundredPercentUsesTheIsoDefault() {
    FiberQualityStandard iso = isoDefault();
    when(standardRepository.findByTenantIdAndTargetTypeAndFiberIdAndIsDefaultTrueAndIsActiveTrue(
            TENANT_ID, FiberQualityTargetType.FIBER, cotton.getId()))
        .thenReturn(Optional.empty());
    when(standardRepository.findByTenantIdAndTargetTypeAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(
            TENANT_ID, FiberQualityTargetType.ISO_CODE, cottonIso.getId()))
        .thenReturn(Optional.of(iso));

    QualityResolution resolution =
        queryService.resolveDefault(
            TENANT_ID, effective(cotton, Map.of(cotton.getId(), new BigDecimal("100.00"))));

    assertThat(resolution.source()).isEqualTo(ProfileSource.ISO_DEFAULT);
    assertThat(resolution.profile()).isSameAs(iso);
  }

  @Test
  void anExactFiberDefaultWinsOverTheIsoDefault() {
    FiberQualityStandard exact = fiberProfile(cotton, "Cotton exact");
    exact.setIsDefault(true);
    when(standardRepository.findByTenantIdAndTargetTypeAndFiberIdAndIsDefaultTrueAndIsActiveTrue(
            TENANT_ID, FiberQualityTargetType.FIBER, cotton.getId()))
        .thenReturn(Optional.of(exact));

    QualityResolution resolution =
        queryService.resolveDefault(
            TENANT_ID, effective(cotton, Map.of(cotton.getId(), new BigDecimal("100"))));

    assertThat(resolution.source()).isEqualTo(ProfileSource.EXACT_FIBER_DEFAULT);
    verify(standardRepository, never())
        .findByTenantIdAndTargetTypeAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(
            any(), any(), any());
  }

  @Test
  void aBlendNeverBorrowsItsDominantComponentsIsoDefault() {
    when(standardRepository.findByTenantIdAndTargetTypeAndFiberIdAndIsDefaultTrueAndIsActiveTrue(
            TENANT_ID, FiberQualityTargetType.FIBER, blend.getId()))
        .thenReturn(Optional.empty());

    QualityResolution resolution =
        queryService.resolveDefault(TENANT_ID, effective(blend, blend.getComposition()));

    assertThat(resolution.source()).isEqualTo(ProfileSource.NONE);
    assertThat(resolution.profile()).isNull();
    verify(standardRepository, never())
        .findByTenantIdAndTargetTypeAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(
            any(), any(), any());
  }

  @Test
  void anExactFiberDefaultOfADifferentCompositionDoesNotApply() {
    FiberQualityStandard exact = fiberProfile(blend, "Blend 60/40");
    exact.setIsDefault(true);
    when(standardRepository.findByTenantIdAndTargetTypeAndFiberIdAndIsDefaultTrueAndIsActiveTrue(
            TENANT_ID, FiberQualityTargetType.FIBER, blend.getId()))
        .thenReturn(Optional.of(exact));

    QualityResolution resolution =
        queryService.resolveDefault(
            TENANT_ID,
            effective(
                blend,
                Map.of(
                    cotton.getId(), new BigDecimal("62.5"), polyesterId, new BigDecimal("37.5"))));

    assertThat(resolution.source()).isEqualTo(ProfileSource.NONE);
  }

  @Test
  void anExplicitProfileMustApplyToTheExactInput() {
    FiberQualityStandard iso = isoDefault();
    when(standardRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT_ID, iso.getId()))
        .thenReturn(Optional.of(iso));

    assertThatThrownBy(
            () ->
                queryService.resolve(
                    TENANT_ID, effective(blend, blend.getComposition()), iso.getId()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_QUALITY_TARGET_MISMATCH");

    UUID unknown = UUID.randomUUID();
    when(standardRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT_ID, unknown))
        .thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                queryService.resolve(TENANT_ID, effective(blend, blend.getComposition()), unknown))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_QUALITY_STANDARD_NOT_FOUND");
  }

  @Test
  void applicableProfilesOnlyReturnProfilesForTheExactInput() {
    FiberQualityStandard exact = fiberProfile(blend, "Blend 60/40");
    when(standardRepository.findByTenantIdAndTargetTypeAndFiberIdAndIsActiveTrue(
            TENANT_ID, FiberQualityTargetType.FIBER, blend.getId()))
        .thenReturn(List.of(exact));

    assertThat(queryService.applicableProfiles(TENANT_ID, effective(blend, blend.getComposition())))
        .containsExactly(exact);
    verify(standardRepository, never())
        .findByTenantIdAndTargetTypeAndIsoCode_IdAndIsActiveTrue(any(), any(), any());
  }

  @Test
  void storedEvaluationKeepsTheManualPathForUnknownSnapshotsAndUnavailableProfiles() {
    UUID productId = blend.getProductId();
    lenient()
        .when(fiberRepository.findInScopeByProductId(FiberCatalog.readScope(TENANT_ID), productId))
        .thenReturn(Optional.of(blend));

    StoredEvaluation unknown =
        queryService.evaluateStored(TENANT_ID, productId, Optional.empty(), null);
    assertThat(unknown.standardOptional()).isEmpty();
    assertThat(unknown.diagnosticCode()).isEqualTo("BATCH_COMPOSITION_UNKNOWN");

    UUID gone = UUID.randomUUID();
    when(standardRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT_ID, gone))
        .thenReturn(Optional.empty());
    StoredEvaluation unavailable =
        queryService.evaluateStored(
            TENANT_ID, productId, Optional.of(blend.getComposition()), gone);
    assertThat(unavailable.diagnosticCode()).isEqualTo("QUALITY_PROFILE_UNAVAILABLE");

    FiberQualityStandard iso = isoDefault();
    when(standardRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT_ID, iso.getId()))
        .thenReturn(Optional.of(iso));
    StoredEvaluation notApplicable =
        queryService.evaluateStored(
            TENANT_ID, productId, Optional.of(blend.getComposition()), iso.getId());
    assertThat(notApplicable.standardOptional()).isEmpty();
    assertThat(notApplicable.diagnosticCode()).isEqualTo("QUALITY_PROFILE_NOT_APPLICABLE");

    StoredEvaluation none =
        queryService.evaluateStored(
            TENANT_ID, productId, Optional.of(blend.getComposition()), null);
    assertThat(none.diagnosticCode()).isEqualTo("NO_APPLICABLE_QUALITY_PROFILE");
    assertThat(none.targetLabel()).isEqualTo("CO 60% / PES 40%");
  }

  @Test
  void storedEvaluationWithoutAFibreDefinitionIsDiagnosed() {
    UUID productId = UUID.randomUUID();
    when(fiberRepository.findInScopeByProductId(FiberCatalog.readScope(TENANT_ID), productId))
        .thenReturn(Optional.empty());

    StoredEvaluation evaluation =
        queryService.evaluateStored(
            TENANT_ID, productId, Optional.of(Map.of(UUID.randomUUID(), BigDecimal.TEN)), null);

    assertThat(evaluation.diagnosticCode()).isEqualTo("FIBER_PRODUCT_NOT_FOUND");
    assertThat(blend.getKind()).isEqualTo(FiberKind.BLEND);
  }

  // ── Review finding 3: the default composition takes the same validation path ──────────────

  @Test
  void defaultCompositionIsValidatedLikeAnExplicitOne() {
    when(fiberRepository.findInScopeByProductId(any(), any())).thenReturn(Optional.of(blend));
    Map<UUID, BigDecimal> definition = blend.getComposition();
    when(validationService.validateEffectiveComposition(definition, TENANT_ID))
        .thenReturn(new FiberValidationService.ResolvedComposition(definition, Map.of()));

    EffectiveComposition effective =
        queryService.resolveEffectiveComposition(TENANT_ID, blend.getProductId(), null);

    assertThat(effective.composition()).isEqualTo(definition);
    verify(validationService).validateEffectiveComposition(definition, TENANT_ID);
  }

  @Test
  void aDefinitionWithADeactivatedComponentIsRejectedLikeTheSameExplicitComposition() {
    when(fiberRepository.findInScopeByProductId(any(), any())).thenReturn(Optional.of(blend));
    Map<UUID, BigDecimal> definition = blend.getComposition();
    when(validationService.validateEffectiveComposition(definition, TENANT_ID))
        .thenThrow(
            new FiberDomainException(
                "Composition component is inactive or obsolete",
                "FIBER_COMPONENT_INACTIVE",
                400,
                new Object[] {polyesterId}));

    assertThatThrownBy(
            () -> queryService.resolveEffectiveComposition(TENANT_ID, blend.getProductId(), null))
        .isInstanceOfSatisfying(
            FiberDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("FIBER_COMPONENT_INACTIVE"));
    assertThatThrownBy(
            () ->
                queryService.resolveEffectiveComposition(
                    TENANT_ID, blend.getProductId(), definition))
        .isInstanceOfSatisfying(
            FiberDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("FIBER_COMPONENT_INACTIVE"));
  }

  @Test
  void aPureDefaultIsValidatedAsItsOwnHundredPercentComponent() {
    when(fiberRepository.findInScopeByProductId(any(), any())).thenReturn(Optional.of(cotton));
    Map<UUID, BigDecimal> pure = Map.of(cotton.getId(), new BigDecimal("100"));
    when(validationService.validateEffectiveComposition(pure, TENANT_ID))
        .thenReturn(new FiberValidationService.ResolvedComposition(pure, Map.of()));

    queryService.resolveEffectiveComposition(TENANT_ID, cotton.getProductId(), null);

    verify(validationService).validateEffectiveComposition(pure, TENANT_ID);
  }
}
