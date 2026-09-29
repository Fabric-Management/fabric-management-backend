package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.dto.CreateFiberQualityStandardRequest;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.product.fiber.dto.FiberApplicableQualityStandardsDto;
import com.fabricmanagement.product.fiber.dto.FiberApplicableQualityStandardsRequest;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.product.fiber.dto.FiberQualityResolutionReason;
import com.fabricmanagement.product.fiber.dto.FiberQualityStandardDto;
import com.fabricmanagement.product.fiber.dto.UpdateFiberQualityStandardRequest;
import com.fabricmanagement.product.fiber.dto.UpdateFiberRequest;
import com.fabricmanagement.production.core.batch.app.BatchOperationsService;
import com.fabricmanagement.production.core.batch.app.BatchService;
import com.fabricmanagement.production.core.batch.domain.BatchSourceType;
import com.fabricmanagement.production.core.batch.dto.BatchDto;
import com.fabricmanagement.production.core.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.core.batch.dto.PartialAcceptanceSplitRequest;
import com.fabricmanagement.production.quality.result.app.FiberTestResultService;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.dto.CreateFiberTestResultRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * FIBER-CATALOG-1 §8 on a real database: tenant quality targets, the shared resolver at batch
 * creation and QC, composition snapshots. Scenarios A11-A16.
 */
class FiberQualityTargetIT extends FiberSourceIntegrationSupport {

  @Autowired private FiberQualityStandardService standardService;
  @Autowired private BatchService batchService;
  @Autowired private BatchOperationsService batchOperationsService;
  @Autowired private FiberTestResultService testResultService;

  @Test
  void a11_isoDefaultAppliesToSharedPureCottonOnlyForItsOwnTenantAndExactFiberWins() {
    UUID tenantA = insertTenant("quality-a");
    UUID tenantB = insertTenant("quality-b");
    useTenant(tenantB, UUID.randomUUID());
    FiberQualityStandardDto foreign = isoProfile("CO", "B default", true);
    useTenant(tenantA, UUID.randomUUID());
    FiberQualityStandardDto isoDefault = isoProfile("CO", "A CO default", true);

    BatchDto batch = batch(canonicalProductId("CO"), null, null);
    assertThat(batch.getQualityStandardId())
        .isEqualTo(isoDefault.getId())
        .isNotEqualTo(foreign.getId());
    assertThat(batch.getComposition())
        .containsOnlyKeys(canonicalFiberId("CO"))
        .allSatisfy((id, share) -> assertThat(share).isEqualByComparingTo("100"));

    FiberQualityStandardDto exact = fiberProfile(canonicalFiberId("CO"), "A exact CO", true);
    assertThat(batch(canonicalProductId("CO"), null, null).getQualityStandardId())
        .isEqualTo(exact.getId());
    FiberApplicableQualityStandardsDto applicable =
        standardService.getApplicable(
            new FiberApplicableQualityStandardsRequest(canonicalProductId("CO"), null));
    assertThat(applicable.resolutionReason())
        .isEqualTo(FiberQualityResolutionReason.EXACT_FIBER_DEFAULT);
    assertThat(applicable.profiles())
        .extracting(FiberQualityStandardDto::getId)
        .containsExactlyInAnyOrder(isoDefault.getId(), exact.getId());
  }

  @Test
  void a12_blendWithoutExactProfileStaysPendingDespiteDominantComponentIsoDefault() {
    UUID tenantId = insertTenant("quality-blend");
    useTenant(tenantId, UUID.randomUUID());
    isoProfile("CO", "CO default", true);
    FiberDto blend = blend("80", "20");

    BatchDto batch = batch(blend.getProductId(), null, null);
    assertThat(batch.getQualityStandardId()).as("no primary-ISO fallback at creation").isNull();
    assertThat(measure(batch.getId(), 5.0))
        .as("no fallback in QC either")
        .isEqualTo(TestApprovalStatus.PENDING);
    assertThat(
            standardService
                .getApplicable(
                    new FiberApplicableQualityStandardsRequest(blend.getProductId(), null))
                .resolutionReason())
        .isEqualTo(FiberQualityResolutionReason.NO_APPLICABLE_DEFAULT);
  }

  @Test
  void a13_explicitProfileMustApplyAndOverridesNeverInheritAProfile() {
    UUID tenantA = insertTenant("explicit-a");
    UUID tenantB = insertTenant("explicit-b");
    useTenant(tenantB, UUID.randomUUID());
    FiberQualityStandardDto foreign = isoProfile("CO", "Foreign", false);
    useTenant(tenantA, UUID.randomUUID());
    FiberQualityStandardDto pesProfile = isoProfile("PES", "PES only", false);
    FiberQualityStandardDto coDefault = isoProfile("CO", "CO default", true);
    FiberQualityStandardDto inactive = isoProfile("CO", "Retired", false);
    standardService.delete(inactive.getId());
    UUID coProduct = canonicalProductId("CO");

    assertCode(() -> batch(coProduct, null, pesProfile.getId()), "FIBER_QUALITY_TARGET_MISMATCH");
    assertCode(() -> batch(coProduct, null, foreign.getId()), "FIBER_QUALITY_STANDARD_NOT_FOUND");
    assertCode(() -> batch(coProduct, null, inactive.getId()), "FIBER_QUALITY_STANDARD_NOT_FOUND");

    Map<UUID, BigDecimal> mixture =
        Map.of(canonicalFiberId("CO"), pct("90"), canonicalFiberId("PES"), pct("10"));
    BatchDto overridden = batch(coProduct, mixture, null);
    assertThat(overridden.getQualityStandardId())
        .as("a pure product overridden with a mixture never inherits the ISO profile")
        .isNull();
    assertCode(() -> batch(coProduct, mixture, coDefault.getId()), "FIBER_QUALITY_TARGET_MISMATCH");
    assertCode(() -> batch(coProduct, Map.of(), null), "FIBER_COMPOSITION_EMPTY");
  }

  @Test
  void a14_snapshotSurvivesBlendEditsAndSplitsWhileNewBatchesUseTheNewComposition() {
    UUID tenantId = insertTenant("snapshot");
    useTenant(tenantId, UUID.randomUUID());
    FiberDto blend = blend("60", "40");
    FiberQualityStandardDto captured = fiberProfile(blend.getId(), "60/40 moisture", true);

    BatchDto oldBatch = batch(blend.getProductId(), null, null);
    assertThat(oldBatch.getQualityStandardId()).isEqualTo(captured.getId());

    fiberService.updateFiber(
        blend.getId(),
        UpdateFiberRequest.builder()
            .version(fiberService.getById(blend.getId()).orElseThrow().getVersion())
            .fiberName(blend.getFiberName())
            .composition(
                Map.of(canonicalFiberId("CO"), pct("55"), canonicalFiberId("PES"), pct("45")))
            .build());

    assertThat(batchService.getById(oldBatch.getId()).orElseThrow().getComposition())
        .containsEntry(canonicalFiberId("CO"), pct("60"));
    assertThat(measure(oldBatch.getId(), 5.0))
        .as("the old batch still matches its captured profile")
        .isEqualTo(TestApprovalStatus.APPROVED);

    BatchDto newBatch = batch(blend.getProductId(), null, null);
    assertThat(newBatch.getQualityStandardId())
        .as("the new composition does not use the stale 60/40 profile")
        .isNull();

    BatchDto child =
        batchOperationsService.splitPartialAcceptance(
            newBatch.getId(),
            PartialAcceptanceSplitRequest.builder()
                .acceptedQuantity(new BigDecimal("100"))
                .reason("split keeps the snapshot")
                .build());
    assertThat(child.getComposition()).isEqualTo(newBatch.getComposition());
  }

  @Test
  void a15_oneDefaultPerTargetUnderConcurrencyAndTargetRulesHold() throws Exception {
    UUID tenantId = insertTenant("defaults");
    useTenant(tenantId, UUID.randomUUID());
    FiberQualityStandardDto first = isoProfile("CO", "First", false);
    FiberQualityStandardDto second = isoProfile("CO", "Second", false);

    runTogether(
        () -> setDefaultAs(tenantId, first.getId()), () -> setDefaultAs(tenantId, second.getId()));

    assertThat(
            count(
                "SELECT count(*) FROM production.prod_fiber_quality_standard "
                    + "WHERE tenant_id = ? AND is_default AND is_active",
                tenantId))
        .isEqualTo(1L);

    assertCode(
        () ->
            standardService.update(
                first.getId(),
                UpdateFiberQualityStandardRequest.builder()
                    .standardName("First")
                    .targetType(FiberQualityTargetType.FIBER)
                    .fiberId(canonicalFiberId("CO"))
                    .moisturePctMax(10.0)
                    .build()),
        "FIBER_QUALITY_TARGET_IMMUTABLE");
    assertCode(
        () ->
            standardService.create(
                CreateFiberQualityStandardRequest.builder()
                    .targetType(FiberQualityTargetType.ISO_CODE)
                    .isoCodeId(sharedIsoId("CO"))
                    .standardName("Empty")
                    .build()),
        "FIBER_QUALITY_STANDARD_EMPTY");
    assertCode(
        () ->
            standardService.create(
                CreateFiberQualityStandardRequest.builder()
                    .targetType(FiberQualityTargetType.ISO_CODE)
                    .isoCodeId(sharedIsoId("CO"))
                    .fiberId(canonicalFiberId("CO"))
                    .standardName("Both")
                    .moisturePctMax(10.0)
                    .build()),
        "FIBER_QUALITY_TARGET_INVALID");
    assertThatThrownBy(
            () ->
                update(
                    "UPDATE production.prod_fiber_quality_standard SET fiber_id = ? WHERE id = ?",
                    canonicalFiberId("CO"),
                    first.getId()))
        .as("the database enforces target XOR")
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("chk_fqs_target_fields");
  }

  @Test
  void a16_allSevenMetricsIncludingUniformityRoundTripAndAStoredInactiveProfileNeverSwitches() {
    UUID tenantId = insertTenant("metrics");
    useTenant(tenantId, UUID.randomUUID());
    FiberQualityStandardDto full =
        standardService.create(
            CreateFiberQualityStandardRequest.builder()
                .targetType(FiberQualityTargetType.ISO_CODE)
                .isoCodeId(sharedIsoId("CO"))
                .standardName("All metrics")
                .isDefault(true)
                .finenessMin(3.5)
                .lengthMmMin(25.0)
                .strengthCndTexMin(25.0)
                .elongationPctMax(10.0)
                .moisturePctMax(9.0)
                .trashContentPctMax(3.0)
                .uniformityIndexMin(80.0)
                .uniformityIndexTarget(83.0)
                .uniformityIndexMax(90.0)
                .build());
    assertThat(standardService.getById(full.getId()).orElseThrow().getUniformityIndexTarget())
        .isEqualTo(83.0);

    BatchDto batch = batch(canonicalProductId("CO"), null, null);
    assertThat(
            testResultService
                .create(
                    CreateFiberTestResultRequest.builder()
                        .batchId(batch.getId())
                        .testDate(Instant.now())
                        .fineness(4.0)
                        .lengthMm(28.0)
                        .strengthCndTex(29.0)
                        .elongationPercent(6.0)
                        .moisturePercent(7.0)
                        .trashContentPercent(2.0)
                        .uniformityIndex(79.0)
                        .build())
                .getApprovalStatus())
        .as("uniformity below its minimum rejects")
        .isEqualTo(TestApprovalStatus.REJECTED);

    FiberQualityStandardDto replacement = isoProfile("CO", "Replacement", false);
    standardService.delete(full.getId());
    standardService.setDefault(replacement.getId());
    assertThat(measure(batch.getId(), 5.0))
        .as("the stored profile is inactive; QC does not switch to the new default")
        .isEqualTo(TestApprovalStatus.PENDING);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private FiberQualityStandardDto isoProfile(String isoCode, String name, boolean isDefault) {
    return standardService.create(
        CreateFiberQualityStandardRequest.builder()
            .targetType(FiberQualityTargetType.ISO_CODE)
            .isoCodeId(sharedIsoId(isoCode))
            .standardName(name)
            .isDefault(isDefault)
            .moisturePctMin(0.0)
            .moisturePctTarget(5.0)
            .moisturePctMax(10.0)
            .build());
  }

  private FiberQualityStandardDto fiberProfile(UUID fiberId, String name, boolean isDefault) {
    return standardService.create(
        CreateFiberQualityStandardRequest.builder()
            .targetType(FiberQualityTargetType.FIBER)
            .fiberId(fiberId)
            .standardName(name)
            .isDefault(isDefault)
            .moisturePctMin(0.0)
            .moisturePctTarget(5.0)
            .moisturePctMax(10.0)
            .build());
  }

  private FiberDto blend(String co, String pes) {
    return fiberService.createFiber(
        CreateFiberRequest.builder()
            .unit("KG")
            .composition(Map.of(canonicalFiberId("CO"), pct(co), canonicalFiberId("PES"), pct(pes)))
            .build());
  }

  private BatchDto batch(UUID productId, Map<UUID, BigDecimal> override, UUID profileId) {
    return batchService.create(
        CreateBatchRequest.builder()
            .productId(productId)
            .productType(ProductType.FIBER)
            .batchCode("FQT-" + UUID.randomUUID().toString().substring(0, 8))
            .quantity(new BigDecimal("500"))
            .unit("KG")
            .sourceType(BatchSourceType.INITIAL_STOCK)
            .composition(override)
            .qualityStandardId(profileId)
            .build());
  }

  private TestApprovalStatus measure(UUID batchId, double moisture) {
    return testResultService
        .create(
            CreateFiberTestResultRequest.builder()
                .batchId(batchId)
                .testDate(Instant.now())
                .moisturePercent(moisture)
                .build())
        .getApprovalStatus();
  }

  private Throwable setDefaultAs(UUID tenantId, UUID profileId) {
    try {
      useTenant(tenantId, UUID.randomUUID());
      standardService.setDefault(profileId);
      return null;
    } catch (Throwable failure) {
      return failure;
    } finally {
      TenantContext.clear();
    }
  }

  private void runTogether(
      java.util.concurrent.Callable<Throwable> first,
      java.util.concurrent.Callable<Throwable> second)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Throwable>> futures = new ArrayList<>();
      for (var task : List.of(first, second)) {
        futures.add(
            executor.submit(
                () -> {
                  start.await(10, TimeUnit.SECONDS);
                  return task.call();
                }));
      }
      start.countDown();
      for (Future<Throwable> future : futures) {
        assertThat(future.get(20, TimeUnit.SECONDS)).isNull();
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static void assertCode(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo(code);
  }

  private static BigDecimal pct(String value) {
    return new BigDecimal(value);
  }
}
