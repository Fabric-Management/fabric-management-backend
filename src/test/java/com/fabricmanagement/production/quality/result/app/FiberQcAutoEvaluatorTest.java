package com.fabricmanagement.production.quality.result.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService.StoredEvaluation;
import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchCompositionSnapshot;
import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FiberQcAutoEvaluatorTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final UUID STANDARD_ID = UUID.randomUUID();
  private static final UUID COTTON_ID = UUID.randomUUID();
  private static final Map<UUID, BigDecimal> SNAPSHOT = Map.of(COTTON_ID, new BigDecimal("100"));

  @Mock private FiberQualityQueryService fiberQualityQueryService;
  @Mock private Batch batch;

  private FiberQcAutoEvaluator evaluator;

  @BeforeEach
  void setUpEvaluationPath() {
    evaluator = new FiberQcAutoEvaluator(fiberQualityQueryService);
    lenient().when(batch.getProductType()).thenReturn(ProductType.FIBER);
    lenient().when(batch.getProductId()).thenReturn(PRODUCT_ID);
    lenient().when(batch.getQualityStandardId()).thenReturn(STANDARD_ID);
    lenient()
        .when(batch.getAttributes())
        .thenReturn(
            Map.of(
                BatchCompositionSnapshot.ATTRIBUTE_KEY,
                BatchCompositionSnapshot.toAttribute(SNAPSHOT)));
  }

  @ParameterizedTest
  @CsvSource({"4.0,APPROVED", "4.5,CONDITIONAL_ACCEPT", "6.0,REJECTED"})
  void preservesExistingDecisionWhenUniformityBoundsAreNull(
      double fineness, TestApprovalStatus expected) {
    FiberQualityStandard standard =
        FiberQualityStandard.builder()
            .finenessMin(3.0)
            .finenessTarget(4.0)
            .finenessMax(5.0)
            .build();
    FiberTestResult result =
        FiberTestResult.builder().fineness(fineness).uniformityIndex(84.0).build();

    assertThat(evaluate(result, standard)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"79.9,REJECTED", "82.0,CONDITIONAL_ACCEPT", "84.0,APPROVED"})
  void evaluatesUniformityIndexAgainstConfiguredBounds(
      double uniformityIndex, TestApprovalStatus expected) {
    FiberQualityStandard standard =
        FiberQualityStandard.builder()
            .uniformityIndexMin(80.0)
            .uniformityIndexTarget(84.0)
            .uniformityIndexMax(86.0)
            .build();
    FiberTestResult result = FiberTestResult.builder().uniformityIndex(uniformityIndex).build();

    assertThat(evaluate(result, standard)).isEqualTo(expected);
  }

  @Test
  void passesTheStoredSnapshotAndStoredProfileToTheSharedResolver() {
    FiberQualityStandard standard = FiberQualityStandard.builder().moisturePctMax(10.0).build();
    when(fiberQualityQueryService.evaluateStored(
            TENANT_ID, PRODUCT_ID, Optional.of(SNAPSHOT), STANDARD_ID))
        .thenReturn(new StoredEvaluation(standard, "Cotton (100%)", null));

    FiberQcAutoEvaluator.EvaluationResult evaluation =
        evaluator.evaluate(
            FiberTestResult.builder().moisturePercent(7.0).build(), batch, TENANT_ID);

    assertThat(evaluation.hasStandard()).isTrue();
    assertThat(evaluation.targetLabel()).isEqualTo("Cotton (100%)");
    assertThat(evaluation.diagnosticCode()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "BATCH_COMPOSITION_UNKNOWN",
        "QUALITY_PROFILE_UNAVAILABLE",
        "QUALITY_PROFILE_NOT_APPLICABLE",
        "NO_APPLICABLE_QUALITY_PROFILE"
      })
  void withoutAnEvaluableProfileTheResultStaysPendingWithADiagnostic(String diagnostic) {
    when(fiberQualityQueryService.evaluateStored(any(), any(), any(), any()))
        .thenReturn(new StoredEvaluation(null, "CO 60% / PES 40%", diagnostic));

    FiberQcAutoEvaluator.EvaluationResult evaluation =
        evaluator.evaluate(
            FiberTestResult.builder().moisturePercent(7.0).build(), batch, TENANT_ID);

    assertThat(evaluation.approvalStatus()).isEqualTo(TestApprovalStatus.PENDING);
    assertThat(evaluation.hasStandard()).isFalse();
    assertThat(evaluation.diagnosticCode()).isEqualTo(diagnostic);
  }

  @Test
  void aMalformedSnapshotIsUnknownNeverPure() {
    when(batch.getAttributes())
        .thenReturn(Map.of(BatchCompositionSnapshot.ATTRIBUTE_KEY, Map.of("not-a-uuid", "60")));
    when(fiberQualityQueryService.evaluateStored(
            TENANT_ID, PRODUCT_ID, Optional.empty(), STANDARD_ID))
        .thenReturn(new StoredEvaluation(null, "Blend", "BATCH_COMPOSITION_UNKNOWN"));

    assertThat(
            evaluator
                .evaluate(FiberTestResult.builder().build(), batch, TENANT_ID)
                .diagnosticCode())
        .isEqualTo("BATCH_COMPOSITION_UNKNOWN");
  }

  @Test
  void nonFiberBatchesAreSkipped() {
    when(batch.getProductType()).thenReturn(ProductType.YARN);

    assertThat(
            evaluator.evaluate(FiberTestResult.builder().build(), batch, TENANT_ID).hasStandard())
        .isFalse();
    verify(fiberQualityQueryService, never()).evaluateStored(any(), any(), any(), any());
  }

  private TestApprovalStatus evaluate(FiberTestResult result, FiberQualityStandard standard) {
    when(fiberQualityQueryService.evaluateStored(
            TENANT_ID, PRODUCT_ID, Optional.of(SNAPSHOT), STANDARD_ID))
        .thenReturn(new StoredEvaluation(standard, "Cotton (100%)", null));

    return evaluator.evaluate(result, batch, TENANT_ID).approvalStatus();
  }
}
