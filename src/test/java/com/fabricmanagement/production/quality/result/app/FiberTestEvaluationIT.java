package com.fabricmanagement.production.quality.result.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.app.FiberQualityStandardService;
import com.fabricmanagement.product.fiber.app.FiberReferenceQueryService;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import com.fabricmanagement.product.fiber.dto.CreateFiberQualityStandardRequest;
import com.fabricmanagement.product.fiber.dto.UpdateFiberQualityStandardRequest;
import com.fabricmanagement.production.core.batch.app.BatchService;
import com.fabricmanagement.production.core.batch.domain.BatchSourceType;
import com.fabricmanagement.production.core.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.Outcome;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.Parameter;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.dto.CreateFiberTestResultRequest;
import com.fabricmanagement.production.quality.result.dto.FiberTestEvaluationDto.FiberTestParameterEvaluationDto;
import com.fabricmanagement.production.quality.result.dto.FiberTestResultDto;
import com.fabricmanagement.production.quality.result.dto.UpdateApprovalRequest;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FiberTestEvaluationIT extends AbstractIntegrationTest {

  @Autowired private SystemTransactionExecutor systemTransactions;
  @Autowired private FiberReferenceQueryService fibreReferences;
  @Autowired private FiberQualityStandardService profiles;
  @Autowired private BatchService batches;
  @Autowired private FiberTestResultService results;
  @Autowired private ObjectMapper json;

  private UUID tenantId;
  private String suffix;

  @BeforeEach
  void createTenant() {
    tenantId = UUID.randomUUID();
    suffix = tenantId.toString().substring(0, 8);
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, status)"
                  + " VALUES (?, ?, ?, 'Alder Beck Spinning Ltd', 'REGULAR', 'ACTIVE')",
              tenantId,
              "ABS-" + suffix,
              "alder-beck-" + suffix);
          return null;
        });
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(TenantContext.SYSTEM_ACTOR_ID);
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void evaluationIsStoredWithTheLimitsItUsed() {
    UUID cottonIsoId = fibreReferences.findIsoCode("CO").orElseThrow().getId();
    var profile =
        profiles.create(
            CreateFiberQualityStandardRequest.builder()
                .targetType(FiberQualityTargetType.ISO_CODE)
                .isoCodeId(cottonIsoId)
                .standardName("Alder Beck cotton")
                .lengthMmMin(25.0)
                .moisturePctMin(0.0)
                .moisturePctTarget(5.0)
                .moisturePctMax(10.0)
                .build());
    UUID batchId = cottonLot(profile.getId());
    FiberTestResultDto created = recordMoisture(batchId);
    var evaluation = created.getEvaluation();

    assertThat(created.getApprovalStatus()).isEqualTo(TestApprovalStatus.PENDING);
    assertThat(evaluation.profileId()).isEqualTo(profile.getId());
    assertThat(evaluation.profileName()).isEqualTo("Alder Beck cotton");
    assertThat(evaluation.diagnosticCode()).isNull();
    assertThat(evaluation.complete()).isFalse();
    assertThat(evaluation.limitDefined()).isTrue();
    assertThat(evaluation.outsideLimits()).isFalse();
    assertThat(evaluation.parameters())
        .containsExactly(
            new FiberTestParameterEvaluationDto(
                Parameter.LENGTH, null, 25.0, null, null, null, Outcome.NOT_MEASURED),
            new FiberTestParameterEvaluationDto(
                Parameter.MOISTURE, 7.0, 0.0, 10.0, 5.0, 2.0, Outcome.WITHIN_LIMITS));
    assertThat(results.getById(created.getId()).orElseThrow().getEvaluation())
        .isEqualTo(evaluation);

    profiles.update(
        profile.getId(),
        UpdateFiberQualityStandardRequest.builder()
            .version(profile.getVersion())
            .standardName("Alder Beck cotton revised")
            .lengthMmMin(25.0)
            .moisturePctMin(0.0)
            .moisturePctTarget(4.0)
            .moisturePctMax(6.0)
            .build());
    var newer = recordMoisture(batchId).getEvaluation();
    assertThat(newer.profileName()).isEqualTo("Alder Beck cotton revised");
    assertThat(newer.outsideLimits()).isTrue();
    assertThat(newer.parameters())
        .contains(
            new FiberTestParameterEvaluationDto(
                Parameter.MOISTURE, 7.0, 0.0, 6.0, 4.0, 3.0, Outcome.OUTSIDE_LIMITS));
    assertThat(results.getById(created.getId()).orElseThrow().getEvaluation())
        .isEqualTo(evaluation);
    assertThat(results.getByBatchId(batchId))
        .extracting(FiberTestResultDto::getEvaluation)
        .containsExactlyInAnyOrder(evaluation, newer);

    results.updateApproval(
        created.getId(),
        new UpdateApprovalRequest(created.getVersion(), TestApprovalStatus.APPROVED, null));
    var approved = results.getById(created.getId()).orElseThrow();
    assertThat(approved.getApprovalStatus()).isEqualTo(TestApprovalStatus.APPROVED);
    assertThat(approved.getEvaluation()).isEqualTo(evaluation);
  }

  @Test
  void anOlderResultKeepsItsStoredStatusAndOmitsTheEvaluation() {
    UUID batchId = cottonLot(null);
    UUID resultId = UUID.randomUUID();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.production_quality_fiber_test_result"
                  + " (id, tenant_id, uid, batch_id, test_date, approval_status)"
                  + " VALUES (?, ?, ?, ?, now(), 'CONDITIONAL_ACCEPT')",
              resultId,
              tenantId,
              "ABS-FTR-" + suffix,
              batchId);
          return null;
        });

    var old = results.getById(resultId).orElseThrow();
    assertThat(old.getApprovalStatus()).isEqualTo(TestApprovalStatus.CONDITIONAL_ACCEPT);
    assertThat(old.getEvaluation()).isNull();
    assertThat(json.valueToTree(old).has("evaluation")).isFalse();
  }

  private UUID cottonLot(UUID profileId) {
    UUID cottonProductId =
        systemTransactions.executeInTransaction(
            jdbc ->
                jdbc.queryForObject(
                    "SELECT f.product_id FROM production.prod_fiber f"
                        + " JOIN production.prod_fiber_iso_code i ON i.id = f.fiber_iso_code_id"
                        + " WHERE f.tenant_id = ? AND i.iso_code = 'CO' AND f.material_source IS NULL",
                    UUID.class,
                    FiberCatalog.OWNER_ID));
    return batches
        .create(
            CreateBatchRequest.builder()
                .productId(cottonProductId)
                .productType(ProductType.FIBER)
                .batchCode("ABS-CO-" + suffix)
                .quantity(new BigDecimal("500"))
                .unit("KG")
                .sourceType(BatchSourceType.INITIAL_STOCK)
                .qualityStandardId(profileId)
                .build())
        .getId();
  }

  private FiberTestResultDto recordMoisture(UUID batchId) {
    return results.create(
        CreateFiberTestResultRequest.builder()
            .batchId(batchId)
            .testDate(Instant.now().minusSeconds(60))
            .moisturePercent(7.0)
            .build());
  }
}
