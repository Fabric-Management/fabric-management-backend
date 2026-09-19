package com.fabricmanagement.production.core.batch.app.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.product.color.domain.Color;
import com.fabricmanagement.product.color.infra.repository.ColorRepository;
import com.fabricmanagement.product.core.app.ProductEvidenceQueryService;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fiber.domain.reference.FiberCertification;
import com.fabricmanagement.product.fiber.infra.repository.FiberCertificationRepository;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.product.qualitygrade.domain.QualityGrade;
import com.fabricmanagement.product.qualitygrade.infra.repository.QualityGradeRepository;
import com.fabricmanagement.production.core.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.core.batch.app.StockAvailabilityQueryService;
import com.fabricmanagement.production.core.batch.domain.*;
import com.fabricmanagement.production.core.batch.infra.repository.*;
import com.fabricmanagement.production.core.stockunit.domain.*;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.sales.salesorder.app.RequirementEvidenceProfileMapper;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverEvidenceEvaluator;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverFingerprint;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.*;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacet;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacetValue;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileBasis;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileInput;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto.Suitability;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Transactional
@Import(OrderCoverEvidenceAdapterIT.CertificatePolicyTestConfig.class)
class OrderCoverEvidenceAdapterIT extends AbstractIntegrationTest {
  @Autowired private OrderCoverEvidenceAdapter adapter;
  @Autowired private StockAvailabilityQueryService availability;
  @Autowired private BatchPrimaryMeasureService measures;
  @Autowired private ProductEvidenceQueryService productReferences;
  @Autowired private BatchLotQuantityIntentRepository intents;
  @Autowired private BatchRepository batches;
  @Autowired private StockUnitRepository units;
  @Autowired private BatchReservationRepository reservations;
  @Autowired private BatchCertificationRepository certifications;
  @Autowired private FixtureBatchCertificateEvidencePolicy fixtureCertificatePolicy;
  @Autowired private ColorRepository colors;
  @Autowired private ProductRepository products;
  @Autowired private QualityGradeRepository grades;
  @Autowired private FiberCertificationRepository fiberCertifications;
  @Autowired private TenantRepository tenants;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;
  private UUID tenantId;

  @BeforeEach
  void tenantContext() {
    fixtureCertificatePolicy.clearDeclarations();
    tenantId = tenant();
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(UUID.randomUUID());
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void batchesBeyondPresentationPageAreAllInspected() {
    Product product = product();
    for (int i = 0; i < 26; i++) batch(product, BatchStatus.AVAILABLE);
    assertThat(
            availability.lots(null, null, product.getId(), null, null, null, PageRequest.of(0, 20)))
        .hasSize(20);
    assertThat(adapter.inspect(requirements(product, "100", "M")).lots()).hasSize(26);
  }

  @Test
  void heldBatchStaysReferencedButCannotContributeSuitableStock() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.ON_HOLD);
    var inputs = adapter.inspect(requirements(product, "10", "M"));
    assertThat(inputs.lots()).hasSize(1);
    assertThat(inputs.lots().getFirst().eligibility()).isEqualTo(Eligibility.EXCLUDED);
    assertThat(inputs.lots().getFirst().reasons()).contains("BATCH_ON_HOLD");
    assertThat(inputs.lots().getFirst().sources())
        .anySatisfy(source -> assertThat(source.id()).isEqualTo(batch.getId()));
  }

  @Test
  void convertsSameDimensionAndNeverGuessesWeightToLength() {
    Product product = product();
    batch(product, BatchStatus.AVAILABLE);
    var converted = adapter.inspect(requirements(product, "2500", "CM"));
    assertThat(converted.demands().getFirst().quantity()).isEqualByComparingTo("25");
    assertThat(converted.demands().getFirst().unit()).isEqualTo("M");
    assertThat(converted.lots().getFirst().sources())
        .anySatisfy(source -> assertThat(source.type()).isEqualTo("PRIMARY_MEASURE_RULE"));
    var unknown = adapter.inspect(requirements(product, "25", "KG"));
    assertThat(unknown.demands().getFirst().quantity()).isNull();
    assertThat(unknown.demands().getFirst().reason()).isEqualTo("UNSUPPORTED_UNIT_CONVERSION");
  }

  @Test
  void mixedGradeCommitmentsStayUnknownAndSourceChangesInvalidateFingerprint() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    var good = grade(true, 1);
    var bad = grade(false, 2);
    piece(batch, good, "100");
    piece(batch, bad, "100");
    var reservation =
        reservations.saveAndFlush(
            BatchReservation.create(
                tenantId,
                batch.getId(),
                UUID.randomUUID(),
                "WORK_ORDER",
                new BigDecimal("50"),
                "M",
                null));
    var before = adapter.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(before.eligibility()).isEqualTo(Eligibility.UNKNOWN);
    assertThat(before.reasons()).contains("COMMITMENT_GRADE_SCOPE_UNKNOWN", "NON_SALEABLE_GRADE");
    assertThat(before.suitableFree()).isNull();
    assertThat(before.sources())
        .anySatisfy(
            source -> {
              assertThat(source.type()).isEqualTo("BATCH_RESERVATION");
              assertThat(source.id()).isEqualTo(reservation.getId());
            });
    reservation.cancel();
    reservations.saveAndFlush(reservation);
    var after = adapter.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(after.suitableFree()).isEqualByComparingTo("100");
    assertThat(after.sourceFingerprint()).isNotEqualTo(before.sourceFingerprint());

    UUID card = color();
    batch.assignColor(card);
    batches.saveAndFlush(batch);
    Requirement colourRequirement =
        profiledRequirement(UUID.randomUUID(), product, colourProfile(card));
    Lot profiled =
        adapter
            .inspect(
                new Requirements(
                    tenantId, UUID.randomUUID(), UUID.randomUUID(), 0, List.of(colourRequirement)))
            .lots()
            .getFirst();
    assertThat(profiled.eligibility()).isEqualTo(Eligibility.ELIGIBLE);
    assertThat(profiled.suitableFree()).isEqualByComparingTo("100");
    assertThat(profiled.suitabilityFor(colourRequirement.lineId()).eligibility())
        .isEqualTo(Eligibility.ELIGIBLE);
  }

  @Test
  void fiberGradeAndShadeUseLotAttributesAndPreserveMissingEvidenceAsUnknown() {
    Product product = product(ProductType.FIBER, "KG");
    Batch matching = batch(product, BatchStatus.AVAILABLE);
    matching.setAttributes(Map.of("fiber_grade", "A_GRADE", "fiber_shade", "OPTICAL_WHITE"));
    batches.saveAndFlush(matching);
    piece(matching, grade(ProductType.FIBER, true, 1), null);

    Batch gradeMismatch = batch(product, BatchStatus.AVAILABLE);
    gradeMismatch.setAttributes(Map.of("fiber_grade", "B_GRADE", "fiber_shade", "OPTICAL_WHITE"));
    batches.saveAndFlush(gradeMismatch);
    piece(gradeMismatch, grade(ProductType.FIBER, true, 2), null);

    Batch shadeMismatch = batch(product, BatchStatus.AVAILABLE);
    shadeMismatch.setAttributes(Map.of("fiber_grade", "A_GRADE", "fiber_shade", "NATURAL"));
    batches.saveAndFlush(shadeMismatch);
    piece(shadeMismatch, grade(ProductType.FIBER, true, 3), null);

    Batch missing = batch(product, BatchStatus.AVAILABLE);
    piece(missing, grade(ProductType.FIBER, true, 4), null);

    RequirementFacet gradeFacet =
        new RequirementFacet(
            RequirementFacet.Kind.FIBRE_GRADE,
            null,
            RequirementFacet.State.BOUNDED,
            RequirementFacet.Comparison.SET_MEMBERSHIP,
            new RequirementFacetValue.Categorical(Set.of("a_grade")),
            decisionBasis());
    RequirementFacet shadeFacet =
        new RequirementFacet(
            RequirementFacet.Kind.FIBRE_SHADE,
            null,
            RequirementFacet.State.BOUNDED,
            RequirementFacet.Comparison.EXACT,
            new RequirementFacetValue.Categorical(Set.of("optical_white")),
            decisionBasis());
    Requirement requirement =
        profiledRequirement(
            UUID.randomUUID(), product, profile(List.of(gradeFacet, shadeFacet)), "5");
    Map<UUID, Lot> lots =
        adapter
            .inspect(
                new Requirements(
                    tenantId, UUID.randomUUID(), UUID.randomUUID(), 0, List.of(requirement)))
            .lots()
            .stream()
            .collect(Collectors.toMap(Lot::lotId, Function.identity()));

    assertThat(lots.get(matching.getId()).suitabilityFor(requirement.lineId()).eligibility())
        .isEqualTo(Eligibility.ELIGIBLE);
    assertThat(lots.get(gradeMismatch.getId()).suitabilityFor(requirement.lineId()).eligibility())
        .isEqualTo(Eligibility.EXCLUDED);
    assertThat(lots.get(shadeMismatch.getId()).suitabilityFor(requirement.lineId()).eligibility())
        .isEqualTo(Eligibility.EXCLUDED);
    assertThat(lots.get(missing.getId()).suitabilityFor(requirement.lineId()).eligibility())
        .isEqualTo(Eligibility.UNKNOWN);
    assertThat(lots.get(missing.getId()).suitabilityFor(requirement.lineId()).reasons())
        .contains("FIBRE_GRADE_UNKNOWN", "FIBRE_SHADE_UNKNOWN");
  }

  @Test
  void homogeneousSaleableStockUsesExistingCanonicalFreeArithmetic() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    piece(batch, grade(true, 1), "100");
    reservations.saveAndFlush(
        BatchReservation.create(
            tenantId,
            batch.getId(),
            UUID.randomUUID(),
            "WORK_ORDER",
            new BigDecimal("2000"),
            "CM",
            null));
    var row = adapter.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(row.eligibility()).isEqualTo(Eligibility.ELIGIBLE);
    assertThat(row.sources())
        .anySatisfy(
            source -> {
              assertThat(source.type()).isEqualTo("QUALITY_GRADE");
              assertThat(source.revision()).isNotNull();
            });
    assertThat(row.suitableFree()).isEqualByComparingTo("80");
    assertThat(row.suitableFree())
        .isEqualByComparingTo(
            availability.lotsForProducts(Set.of(product.getId())).getFirst().free());
  }

  @Test
  void unsupportedCommitmentUnitCannotBeHiddenByCanonicalZero() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    piece(batch, grade(true, 1), "100");
    reservations.saveAndFlush(
        BatchReservation.create(
            tenantId,
            batch.getId(),
            UUID.randomUUID(),
            "WORK_ORDER",
            new BigDecimal("20"),
            "KG",
            null));
    var row = adapter.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(row.eligibility()).isEqualTo(Eligibility.UNKNOWN);
    assertThat(row.reasons()).contains("UNIT_MISMATCH");
    assertThat(row.suitableFree()).isNull();
  }

  @Test
  void missingLengthInOneReleasedPieceDoesNotBecomeKnownPartialTotal() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    var grade = grade(true, 1);
    piece(batch, grade, "100");
    piece(batch, grade, null);
    var row = adapter.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(row.eligibility()).isEqualTo(Eligibility.UNKNOWN);
    assertThat(row.reasons()).contains("PRIMARY_MEASUREMENT_UNKNOWN");
  }

  @Test
  void unassignedGradeAndFallbackAreUnknown() {
    Product product = product();
    Batch fallback = batch(product, BatchStatus.AVAILABLE);
    assertThat(adapter.inspect(requirements(product, "80", "M")).lots().getFirst().reasons())
        .contains("BATCH_FALLBACK_QUALITY_UNKNOWN");
    piece(fallback, null, "100");
    var row = adapter.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(row.reasons()).contains("QUALITY_GRADE_UNKNOWN");
    assertThat(row.suitableFree()).isNull();
  }

  @Test
  void tenantMismatchIsRejectedAndOtherTenantsPopulationIsInvisible() {
    Product product = product();
    batch(product, BatchStatus.AVAILABLE);
    Requirements original = requirements(product, "100", "M");
    UUID otherTenant = tenant();
    TenantContext.setCurrentTenantId(otherTenant);
    assertThatThrownBy(() -> adapter.inspect(original))
        .isInstanceOf(
            com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException.class);
    var other =
        new Requirements(
            otherTenant,
            original.orderId(),
            original.caseId(),
            original.orderVersion(),
            original.lines());
    assertThat(adapter.inspect(other).lots()).isEmpty();
  }

  @Test
  void lockingSeamReadsSameInputsInsideCallerTransaction() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    piece(batch, grade(true, 1), "100");
    Requirements requirements = requirements(product, "80", "M");
    var before = adapter.inspect(requirements);
    var locked = adapter.lockAndInspect(requirements);
    assertThat(OrderCoverFingerprint.of(locked)).isEqualTo(OrderCoverFingerprint.of(before));
    assertThat(entityManager.createNativeQuery("show lock_timeout").getSingleResult())
        .isEqualTo("5s");
  }

  @Test
  void noBatchesStillUsesCatalogMeasureAndProvesKnownShortfallForCompleteDemand() {
    Product product = product();
    Requirements original = requirements(product, "8000", "CM");
    Requirement line = original.lines().getFirst();
    Requirements complete =
        new Requirements(
            tenantId,
            original.orderId(),
            original.caseId(),
            0,
            List.of(
                new Requirement(
                    line.lineId(),
                    0,
                    product.getId(),
                    line.createdAt(),
                    line.requested(),
                    line.unit(),
                    true,
                    null,
                    "typed-test",
                    null)));
    var inputs = adapter.inspect(complete);
    assertThat(inputs.lots()).isEmpty();
    assertThat(inputs.demands().getFirst().quantity()).isEqualByComparingTo("80");
    assertThat(inputs.demands().getFirst().unit()).isEqualTo("M");
    assertThat(inputs.demands().getFirst().sources())
        .anySatisfy(
            source -> {
              assertThat(source.type()).isEqualTo("PRODUCT");
              assertThat(source.id()).isEqualTo(product.getId());
              assertThat(source.revision()).isEqualTo(product.getVersion());
            });
    var evidence = OrderCoverEvidenceEvaluator.evaluate(complete, inputs).getFirst();
    assertThat(evidence.suitability()).isEqualTo(Suitability.NO_MATCH);
    assertThat(evidence.shortfall().value()).isEqualTo("80");
  }

  @Test
  void sameProductLotIsMatchedPerLineAndUnsupportedCertificationStaysUnknown() {
    Product product = product();
    UUID cardX = color();
    UUID cardY = color();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    batch.assignColor(cardX);
    piece(batch, grade(true, 1), "100");
    FiberCertification gotsCertification =
        fiberCertifications.saveAndFlush(
            FiberCertification.builder()
                .certificationCode("GOTS")
                .certificationName("GOTS fixture")
                .build());
    certifications.saveAndFlush(
        BatchCertification.builder()
            .batch(batch)
            .certification(gotsCertification)
            .scope(BatchCertificationScope.FACILITY)
            .certificateKind(BatchCertificateKind.SCOPE)
            .validFrom(java.time.LocalDate.of(2026, 1, 1))
            .validUntil(java.time.LocalDate.of(2027, 1, 1))
            .build());
    certifications.saveAndFlush(
        BatchCertification.builder()
            .batch(batch)
            .certification(gotsCertification)
            .scope(BatchCertificationScope.BATCH)
            .certificateKind(BatchCertificateKind.TRANSACTION)
            .validFrom(java.time.LocalDate.of(2026, 1, 1))
            .build());

    Requirement matching =
        profiledRequirement(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), product, colourProfile(cardX));
    Requirement different =
        profiledRequirement(
            UUID.fromString("00000000-0000-0000-0000-000000000002"), product, colourProfile(cardY));
    Requirement gotsRequirement =
        profiledRequirement(
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            product,
            certificationProfile("GOTS"));
    Requirements requirements =
        new Requirements(
            tenantId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            0,
            List.of(matching, different, gotsRequirement));

    var inputs = adapter.inspect(requirements);
    var evidence = OrderCoverEvidenceEvaluator.evaluate(requirements, inputs);
    var byLine =
        evidence.stream().collect(Collectors.toMap(line -> line.lineId(), Function.identity()));

    assertThat(byLine.get(matching.lineId()).suitability()).isEqualTo(Suitability.EXACT);
    assertThat(byLine.get(different.lineId()).suitability()).isEqualTo(Suitability.NO_MATCH);
    assertThat(gotsRequirement.complete()).isTrue();
    assertThat(byLine.get(gotsRequirement.lineId()).suitability()).isEqualTo(Suitability.UNKNOWN);
    assertThat(byLine.get(gotsRequirement.lineId()).controlReasons())
        .contains("FACET_EVIDENCE_UNKNOWN:CERTIFICATION:GOTS");
    assertThat(inputs.lots().getFirst().facts())
        .filteredOn(fact -> fact.source().type().equals("BATCH_CERTIFICATION"))
        .hasSize(2);
  }

  @Test
  void acceptedFixtureCertificatePolicyEvaluatesSetsWithoutOpeningTheGotsBoundary() {
    Product product = product();
    FiberCertification fixture =
        fiberCertifications.saveAndFlush(
            FiberCertification.builder()
                .certificationCode(FixtureBatchCertificateEvidencePolicy.SCHEME)
                .certificationName("Accepted certificate-policy fixture")
                .build());

    Batch renewed = eligibleBatch(product);
    certificate(
        renewed,
        fixture,
        BatchCertificateKind.SCOPE,
        BatchCertificationScope.FACILITY,
        "2000-01-01",
        "2001-01-01");
    certificate(
        renewed,
        fixture,
        BatchCertificateKind.SCOPE,
        BatchCertificationScope.FACILITY,
        "2001-01-02",
        "2100-01-01");

    Batch expiredAuthoritative = eligibleBatch(product);
    certificate(
        expiredAuthoritative,
        fixture,
        BatchCertificateKind.SCOPE,
        BatchCertificationScope.FACILITY,
        "2000-01-01",
        "2001-01-01");
    fixtureCertificatePolicy.declareAuthoritative(expiredAuthoritative);

    Batch expiredIncomplete = eligibleBatch(product);
    certificate(
        expiredIncomplete,
        fixture,
        BatchCertificateKind.SCOPE,
        BatchCertificationScope.FACILITY,
        "2000-01-01",
        "2001-01-01");

    Batch outOfCoverageAuthoritative = eligibleBatch(product);
    certificate(
        outOfCoverageAuthoritative,
        fixture,
        BatchCertificateKind.SCOPE,
        BatchCertificationScope.BATCH,
        "2000-01-01",
        "2100-01-01");
    fixtureCertificatePolicy.declareAuthoritative(outOfCoverageAuthoritative);

    Batch outOfCoverageIncomplete = eligibleBatch(product);
    certificate(
        outOfCoverageIncomplete,
        fixture,
        BatchCertificateKind.SCOPE,
        BatchCertificationScope.BATCH,
        "2000-01-01",
        "2100-01-01");

    Batch unclassified = eligibleBatch(product);
    certificate(
        unclassified, fixture, null, BatchCertificationScope.FACILITY, "2000-01-01", "2100-01-01");
    fixtureCertificatePolicy.declareAuthoritative(unclassified);

    Batch absent = eligibleBatch(product);
    fixtureCertificatePolicy.declareAuthoritative(absent);

    Batch mixed = eligibleBatch(product);
    certificate(
        mixed,
        fixture,
        BatchCertificateKind.SCOPE,
        BatchCertificationScope.FACILITY,
        "2000-01-01",
        "2100-01-01");
    certificate(
        mixed,
        fixture,
        BatchCertificateKind.TRANSACTION,
        BatchCertificationScope.BATCH,
        "2000-01-01",
        "2001-01-01");
    fixtureCertificatePolicy.declareAuthoritative(mixed);

    Requirement scope =
        profiledRequirement(
            UUID.randomUUID(),
            product,
            certificationProfile(
                FixtureBatchCertificateEvidencePolicy.SCHEME,
                RequirementFacet.Comparison.ALL,
                "SCOPE"));
    Requirement all =
        profiledRequirement(
            UUID.randomUUID(),
            product,
            certificationProfile(
                FixtureBatchCertificateEvidencePolicy.SCHEME,
                RequirementFacet.Comparison.ALL,
                "SCOPE",
                "TRANSACTION"));
    Requirement any =
        profiledRequirement(
            UUID.randomUUID(),
            product,
            certificationProfile(
                FixtureBatchCertificateEvidencePolicy.SCHEME,
                RequirementFacet.Comparison.ANY,
                "SCOPE",
                "TRANSACTION"));
    Requirements requirements =
        new Requirements(
            tenantId, UUID.randomUUID(), UUID.randomUUID(), 0, List.of(scope, all, any));

    Map<UUID, Lot> lots =
        adapter.inspect(requirements).lots().stream()
            .collect(Collectors.toMap(Lot::lotId, Function.identity()));

    assertThat(lots.get(renewed.getId()).suitabilityFor(scope.lineId()).eligibility())
        .isEqualTo(Eligibility.ELIGIBLE);
    assertThat(lots.get(expiredAuthoritative.getId()).suitabilityFor(scope.lineId()).eligibility())
        .isEqualTo(Eligibility.EXCLUDED);
    assertThat(lots.get(expiredIncomplete.getId()).suitabilityFor(scope.lineId()).eligibility())
        .isEqualTo(Eligibility.UNKNOWN);
    assertThat(
            lots.get(outOfCoverageAuthoritative.getId())
                .suitabilityFor(scope.lineId())
                .eligibility())
        .isEqualTo(Eligibility.EXCLUDED);
    assertThat(
            lots.get(outOfCoverageIncomplete.getId()).suitabilityFor(scope.lineId()).eligibility())
        .isEqualTo(Eligibility.UNKNOWN);
    assertThat(lots.get(unclassified.getId()).suitabilityFor(scope.lineId()).eligibility())
        .isEqualTo(Eligibility.UNKNOWN);
    assertThat(lots.get(absent.getId()).suitabilityFor(scope.lineId()).eligibility())
        .isEqualTo(Eligibility.UNKNOWN);
    assertThat(lots.get(mixed.getId()).suitabilityFor(all.lineId()).eligibility())
        .isEqualTo(Eligibility.EXCLUDED);
    assertThat(lots.get(mixed.getId()).suitabilityFor(any.lineId()).eligibility())
        .isEqualTo(Eligibility.ELIGIBLE);
  }

  @Test
  void fictionalFabricFixtureIsCompleteWhileMissingLotEvidenceKeepsSuitabilityUnknown() {
    Product product = product();
    UUID cardX = color();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    batch.assignColor(cardX);
    piece(batch, grade(true, 1), "6000");
    Requirement requirement =
        profiledRequirement(UUID.randomUUID(), product, fabricFixtureProfile(cardX), "5000");
    Requirements requirements =
        new Requirements(tenantId, UUID.randomUUID(), UUID.randomUUID(), 0, List.of(requirement));

    assertThat(requirement.profile().complete()).isTrue();

    var evidence =
        OrderCoverEvidenceEvaluator.evaluate(requirements, adapter.inspect(requirements))
            .getFirst();

    assertThat(evidence.suitability()).isEqualTo(Suitability.UNKNOWN);
    assertThat(evidence.controlReasons())
        .contains(
            "FACET_EVIDENCE_UNKNOWN:WIDTH:FINISHED_OPEN",
            "FACET_EVIDENCE_UNKNOWN:WEIGHT:FINISHED",
            "FACET_EVIDENCE_UNKNOWN:SHADE_APPROVAL:BULK_LOT",
            "FACET_EVIDENCE_UNKNOWN:CERTIFICATION:GOTS")
        .doesNotContain("REQUIREMENT_COMPLETENESS_UNKNOWN");
  }

  @Test
  void knownFacetMismatchExcludesEvenWhenBaseLotEvidenceIsUnknown() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    batch.assignColor(color());
    Requirement requirement =
        profiledRequirement(UUID.randomUUID(), product, colourProfile(color()));
    Requirements requirements =
        new Requirements(tenantId, UUID.randomUUID(), UUID.randomUUID(), 0, List.of(requirement));

    Lot lot = adapter.inspect(requirements).lots().getFirst();

    assertThat(lot.eligibility()).isEqualTo(Eligibility.UNKNOWN);
    assertThat(lot.suitabilityFor(requirement.lineId()).eligibility())
        .isEqualTo(Eligibility.EXCLUDED);
    assertThat(lot.suitabilityFor(requirement.lineId()).reasons())
        .contains("COLOUR_IDENTITY_MISMATCH");
  }

  @Test
  void differingGradeReferencePopulationReturnsUnknownInsteadOfNullPointerException() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    piece(batch, grade(true, 1), "100");
    var missingReferences = mock(QualityGradeQueryService.class);
    var subject =
        new OrderCoverEvidenceAdapter(
            availability,
            measures,
            batches,
            units,
            intents,
            reservations,
            org.mockito.Mockito.mock(BatchCertificationRepository.class),
            List.of(),
            missingReferences,
            productReferences,
            entityManager,
            java.time.Clock.systemUTC());
    var row = subject.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(row.eligibility()).isEqualTo(Eligibility.UNKNOWN);
    assertThat(row.suitableFree()).isNull();
    assertThat(row.reasons()).contains("QUALITY_GRADE_UNKNOWN");
    assertThat(row.comparisons())
        .anySatisfy(
            comparison -> {
              assertThat(comparison.dimension()).isEqualTo("QUALITY_SALEABILITY");
              assertThat(comparison.result()).isEqualTo(ComparisonResult.UNKNOWN);
            });
  }

  @Test
  void lockingSeamPreservesPendingCallerEdits() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    StockUnit piece = piece(batch, grade(true, 1), "100");
    long version = piece.getVersion();
    piece.recordLength(new BigDecimal("75"), "M");
    var row = adapter.lockAndInspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(piece.getLength()).isEqualByComparingTo("75");
    assertThat(piece.getVersion()).isGreaterThan(version);
    assertThat(row.suitableFree()).isEqualByComparingTo("75");
    assertThat(entityManager.contains(piece)).isTrue();
  }

  @Test
  void staleManagedRowsAreRejectedRatherThanSilentlyRefreshedOrUsed() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    piece(batch, grade(true, 1), "100");
    long version = batch.getVersion();
    entityManager
        .createNativeQuery(
            "update production.production_execution_batch set version = version + 1"
                + " where tenant_id = :tenant and id = :id")
        .setParameter("tenant", tenantId)
        .setParameter("id", batch.getId())
        .executeUpdate();
    assertThatThrownBy(() -> adapter.lockAndInspect(requirements(product, "80", "M")))
        .isInstanceOf(OptimisticLockException.class);
    assertThat(batch.getVersion()).isEqualTo(version);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void measuresDistinctConcurrentOrdersContendingForOnePopularProduct() throws Exception {
    var transaction = new TransactionTemplate(transactionManager);
    Product product =
        transaction.execute(
            status -> {
              Product created = product();
              piece(batch(created, BatchStatus.AVAILABLE), grade(true, 1), "100");
              return created;
            });
    int count = 8;
    var ready = new CountDownLatch(count);
    var start = new CountDownLatch(1);
    List<Future<Long>> futures = new ArrayList<>();
    try (var executor = Executors.newFixedThreadPool(count)) {
      for (int i = 0; i < count; i++) {
        // Independent order and line identities; only their product is shared.
        Requirements order = requirements(product, "80", "M");
        futures.add(
            executor.submit(
                () -> {
                  TenantContext.setCurrentTenantId(tenantId);
                  TenantContext.setCurrentUserId(UUID.randomUUID());
                  ready.countDown();
                  try {
                    if (!start.await(10, TimeUnit.SECONDS))
                      throw new AssertionError("Start barrier timed out");
                    long begin = System.nanoTime();
                    transaction.executeWithoutResult(
                        status -> {
                          var inputs = adapter.lockAndInspect(order);
                          assertThat(inputs.lots().getFirst().suitableFree())
                              .isEqualByComparingTo("100");
                          // Controlled representative downstream work; these locks remain held
                          // until commit.
                          try {
                            Thread.sleep(100);
                          } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(ex);
                          }
                        });
                    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
                  } finally {
                    TenantContext.clear();
                  }
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      long begin = System.nanoTime();
      start.countDown();
      List<Long> durations = new ArrayList<>();
      for (var future : futures) durations.add(future.get(30, TimeUnit.SECONDS));
      long wall = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
      durations.sort(Long::compareTo);
      System.out.printf(
          Locale.ROOT,
          "ORDER_COVER_CONTENTION orders=%d products=1 hold_ms=100 success=%d timeout=0 p50_ms=%d p95_ms=%d max_ms=%d wall_ms=%d%n",
          count,
          durations.size(),
          durations.get(3),
          durations.get(7),
          durations.getLast(),
          wall);
      assertThat(durations).hasSize(count);
    } finally {
      start.countDown();
    }
  }

  private UUID tenant() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Tenant tenant = Tenant.create("Evidence " + suffix, "EVID-" + suffix);
    tenant.activate("test");
    return tenants.saveAndFlush(tenant).getId();
  }

  private Product product() {
    return product(ProductType.FABRIC, "M");
  }

  private Product product(ProductType productType, String unit) {
    Product product = Product.create(productType, unit);
    product.setTenantId(tenantId);
    return products.saveAndFlush(product);
  }

  private Batch batch(Product product, BatchStatus status) {
    var batch =
        Batch.builder()
            .productId(product.getId())
            .productType(product.getProductType())
            .batchCode("EVID-" + UUID.randomUUID().toString().substring(0, 8))
            .quantity(new BigDecimal("200"))
            .unit(product.getUnit())
            .reservedQuantity(BigDecimal.ZERO)
            .consumedQuantity(BigDecimal.ZERO)
            .wasteQuantity(BigDecimal.ZERO)
            .status(status)
            .sourceType(BatchSourceType.INITIAL_STOCK)
            .build();
    batch.setTenantId(tenantId);
    return batches.saveAndFlush(batch);
  }

  private QualityGrade grade(boolean saleable, int rank) {
    return grade(ProductType.FABRIC, saleable, rank);
  }

  private QualityGrade grade(ProductType productType, boolean saleable, int rank) {
    return grades.saveAndFlush(
        QualityGrade.create(
            tenantId,
            productType,
            "E" + UUID.randomUUID().toString().substring(0, 6),
            "Evidence grade",
            rank,
            BigDecimal.ONE,
            saleable,
            false,
            null,
            false));
  }

  private StockUnit piece(Batch batch, QualityGrade grade, String metres) {
    PackageType packageType =
        switch (batch.getProductType()) {
          case FIBER -> PackageType.BALE;
          case YARN -> PackageType.CONE;
          case FABRIC -> PackageType.ROLL;
          case CHEMICAL -> PackageType.DRUM;
          case CONSUMABLE -> PackageType.CARTON;
        };
    var piece =
        StockUnit.create(
            tenantId,
            batch.getId(),
            batch.getProductType(),
            "EVID-" + UUID.randomUUID(),
            null,
            packageType,
            new BigDecimal("10"),
            null,
            "KG",
            null,
            StockUnitSourceType.PRODUCTION,
            UUID.randomUUID(),
            QualityDisposition.RELEASED);
    if (metres != null) piece.recordLength(new BigDecimal(metres), "M");
    if (grade != null) piece.changeGrade(grade.getId());
    return units.saveAndFlush(piece);
  }

  private UUID color() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return colors
        .saveAndFlush(Color.create(tenantId, "EVID-" + suffix, "Evidence colour", null))
        .getId();
  }

  private Requirements requirements(Product product, String quantity, String unit) {
    var line =
        new Requirement(
            UUID.randomUUID(),
            0,
            product.getId(),
            Instant.EPOCH,
            new BigDecimal(quantity),
            unit,
            false,
            "REQUIREMENT_COMPLETENESS_UNKNOWN",
            "test",
            null);
    return new Requirements(tenantId, UUID.randomUUID(), UUID.randomUUID(), 0, List.of(line));
  }

  private Requirement profiledRequirement(
      UUID lineId, Product product, RequirementProfileInput input) {
    return profiledRequirement(lineId, product, input, "80");
  }

  private Requirement profiledRequirement(
      UUID lineId, Product product, RequirementProfileInput input, String requested) {
    UUID profileId = UUID.randomUUID();
    RequirementProfileSnapshot profile =
        RequirementProfileSnapshot.resolve(profileId, 1, input, null, false);
    return new Requirement(
        lineId,
        0,
        product.getId(),
        Instant.EPOCH,
        new BigDecimal(requested),
        product.getUnit(),
        profile.complete(),
        null,
        profile.fingerprint(),
        RequirementEvidenceProfileMapper.toPort(profile),
        null);
  }

  private RequirementProfileInput colourProfile(UUID colourId) {
    return profile(
        new RequirementFacet(
            RequirementFacet.Kind.COLOUR_IDENTITY,
            "card",
            RequirementFacet.State.BOUNDED,
            RequirementFacet.Comparison.EXACT,
            new RequirementFacetValue.ColourIdentity(colourId),
            decisionBasis()));
  }

  private RequirementProfileInput certificationProfile(String scheme) {
    return certificationProfile(scheme, RequirementFacet.Comparison.ALL, "SCOPE", "TRANSACTION");
  }

  private RequirementProfileInput certificationProfile(
      String scheme, RequirementFacet.Comparison comparison, String... kinds) {
    return profile(
        new RequirementFacet(
            RequirementFacet.Kind.CERTIFICATION,
            scheme,
            RequirementFacet.State.BOUNDED,
            comparison,
            new RequirementFacetValue.Certification(
                Arrays.stream(kinds)
                    .map(kind -> new RequirementFacetValue.CertificateRef(scheme, kind))
                    .toList()),
            decisionBasis()));
  }

  private Batch eligibleBatch(Product product) {
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    piece(batch, grade(true, 1), "100");
    return batch;
  }

  private BatchCertification certificate(
      Batch batch,
      FiberCertification certification,
      BatchCertificateKind kind,
      BatchCertificationScope scope,
      String validFrom,
      String validUntil) {
    return certifications.saveAndFlush(
        BatchCertification.builder()
            .batch(batch)
            .certification(certification)
            .scope(scope)
            .certificateKind(kind)
            .validFrom(LocalDate.parse(validFrom))
            .validUntil(LocalDate.parse(validUntil))
            .build());
  }

  private RequirementProfileInput fabricFixtureProfile(UUID colourId) {
    RequirementFacet.DecisionBasis basis = decisionBasis();
    RequirementFacet.DecisionBasis authorisedOpen =
        new RequirementFacet.DecisionBasis(
            RequirementFacet.DecisionBasis.Source.AUTHORISED_DECISION,
            "FIXTURE-FABRIC-01-origin-open",
            UUID.randomUUID(),
            Instant.parse("2026-09-18T10:00:00Z"));
    List<RequirementFacet> facets =
        List.of(
            new RequirementFacet(
                RequirementFacet.Kind.WIDTH,
                "finished_open",
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.MINIMUM,
                new RequirementFacetValue.Width(
                    new RequirementFacetValue.NumericBounds(
                        RequirementFacetValue.BoundType.MIN,
                        null,
                        new BigDecimal("150.0"),
                        null,
                        true,
                        false,
                        "cm"),
                    RequirementFacetValue.WidthForm.OPEN,
                    RequirementFacetValue.MaterialState.FINISHED),
                basis),
            new RequirementFacet(
                RequirementFacet.Kind.WEIGHT,
                "finished",
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.RANGE,
                new RequirementFacetValue.Weight(
                    new RequirementFacetValue.NumericBounds(
                        RequirementFacetValue.BoundType.RANGE,
                        null,
                        new BigDecimal("174.6"),
                        new BigDecimal("185.4"),
                        true,
                        true,
                        "g/m²")),
                basis),
            new RequirementFacet(
                RequirementFacet.Kind.COLOUR_IDENTITY,
                "card",
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.EXACT,
                new RequirementFacetValue.ColourIdentity(colourId),
                basis),
            new RequirementFacet(
                RequirementFacet.Kind.SHADE_APPROVAL,
                "bulk_lot",
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.EXACT,
                new RequirementFacetValue.ShadeApproval(
                    true, RequirementFacetValue.ApprovalKind.BULK_LOT),
                basis),
            new RequirementFacet(
                RequirementFacet.Kind.CERTIFICATION,
                "GOTS",
                RequirementFacet.State.BOUNDED,
                RequirementFacet.Comparison.ALL,
                new RequirementFacetValue.Certification(
                    List.of(
                        new RequirementFacetValue.CertificateRef("GOTS", "SCOPE"),
                        new RequirementFacetValue.CertificateRef("GOTS", "TRANSACTION"))),
                basis),
            new RequirementFacet(
                RequirementFacet.Kind.ORIGIN,
                "fabric_made",
                RequirementFacet.State.UNCONSTRAINED,
                RequirementFacet.Comparison.NONE,
                null,
                authorisedOpen),
            new RequirementFacet(
                RequirementFacet.Kind.YARN_COUNT,
                null,
                RequirementFacet.State.UNCONSTRAINED,
                RequirementFacet.Comparison.NONE,
                null,
                authorisedOpen),
            new RequirementFacet(
                RequirementFacet.Kind.YARN_TWIST,
                null,
                RequirementFacet.State.UNCONSTRAINED,
                RequirementFacet.Comparison.NONE,
                null,
                authorisedOpen),
            new RequirementFacet(
                RequirementFacet.Kind.YARN_CONSTRUCTION,
                null,
                RequirementFacet.State.UNCONSTRAINED,
                RequirementFacet.Comparison.NONE,
                null,
                authorisedOpen));
    Instant decidedAt = Instant.parse("2026-09-18T10:00:00Z");
    UUID actor = basis.actorId();
    return new RequirementProfileInput(
        new RequirementProfileBasis(
            RequirementProfileBasis.Kind.LINE_EXPLICIT,
            null,
            null,
            null,
            null,
            actor,
            decidedAt,
            "FIXTURE-FABRIC-01"),
        "FABRIC_LINE_EXPLICIT_V1",
        "SALES_REQ_1_RESOLUTION_V1",
        facets.stream().map(RequirementFacet::identity).collect(Collectors.toSet()),
        facets,
        List.of(),
        List.of());
  }

  private RequirementProfileInput profile(RequirementFacet facet) {
    return profile(List.of(facet));
  }

  private RequirementProfileInput profile(List<RequirementFacet> facets) {
    Instant decidedAt = Instant.parse("2026-09-18T10:00:00Z");
    UUID actor = UUID.randomUUID();
    return new RequirementProfileInput(
        new RequirementProfileBasis(
            RequirementProfileBasis.Kind.LINE_EXPLICIT,
            null,
            null,
            null,
            null,
            actor,
            decidedAt,
            "fixture-contract"),
        "fixture-v1",
        "sales-req-v1",
        facets.stream().map(RequirementFacet::identity).collect(Collectors.toSet()),
        facets,
        List.of(),
        List.of());
  }

  private RequirementFacet.DecisionBasis decisionBasis() {
    return new RequirementFacet.DecisionBasis(
        RequirementFacet.DecisionBasis.Source.CUSTOMER_INSTRUCTION,
        "fixture-contract",
        UUID.randomUUID(),
        Instant.parse("2026-09-18T10:00:00Z"));
  }

  @TestConfiguration
  static class CertificatePolicyTestConfig {
    @Bean
    FixtureBatchCertificateEvidencePolicy acceptedFixtureCertificatePolicy() {
      return new FixtureBatchCertificateEvidencePolicy();
    }
  }
}
