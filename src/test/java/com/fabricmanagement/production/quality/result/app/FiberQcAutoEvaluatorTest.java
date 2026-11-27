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
import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchCompositionSnapshot;
import com.fabricmanagement.production.quality.result.app.FiberQcAutoEvaluator.EvaluationResult;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.Outcome;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.Parameter;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.ParameterEvaluation;
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
  @CsvSource({"4.0,APPROVED", "4.5,APPROVED", "6.0,REJECTED"})
  void preservesFinenessComparisonWhenUniformityBoundsAreNull(
      double fineness, TestApprovalStatus expected) {
    FiberQualityStandard standard =
        FiberQualityStandard.builder()
            .finenessMin(3.0)
            .finenessTarget(4.0)
            .finenessMax(5.0)
            .build();
    FiberTestResult result =
        FiberTestResult.builder().fineness(fineness).uniformityIndex(84.0).build();

    EvaluationResult evaluated = evaluate(result, standard, "CO");

    assertThat(evaluated.approvalStatus()).isEqualTo(expected);
    assertThat(evaluated.evaluation().parameters())
        .extracting(ParameterEvaluation::parameter)
        .containsExactly(Parameter.FINENESS, Parameter.UNIFORMITY_INDEX);
    assertThat(parameter(evaluated, Parameter.UNIFORMITY_INDEX).outcome())
        .isEqualTo(Outcome.NO_LIMIT);
  }

  @ParameterizedTest
  @CsvSource({
    "79.9,REJECTED,OUTSIDE_LIMITS",
    "80.0,APPROVED,WITHIN_LIMITS",
    "82.0,APPROVED,WITHIN_LIMITS",
    "84.0,APPROVED,WITHIN_LIMITS",
    "86.0,APPROVED,WITHIN_LIMITS",
    "86.1,REJECTED,OUTSIDE_LIMITS"
  })
  void evaluatesUniformityIndexAgainstConfiguredBounds(
      double uniformityIndex, TestApprovalStatus expected, Outcome outcome) {
    FiberQualityStandard standard =
        FiberQualityStandard.builder()
            .uniformityIndexMin(80.0)
            .uniformityIndexTarget(84.0)
            .uniformityIndexMax(86.0)
            .build();

    EvaluationResult evaluated =
        evaluate(
            FiberTestResult.builder().uniformityIndex(uniformityIndex).build(), standard, null);

    assertThat(evaluated.approvalStatus()).isEqualTo(expected);
    assertThat(evaluated.evaluation().complete()).isTrue();
    assertThat(evaluated.evaluation().limitDefined()).isTrue();
    assertThat(evaluated.evaluation().outsideLimits()).isEqualTo(outcome == Outcome.OUTSIDE_LIMITS);
    assertThat(parameter(evaluated, Parameter.UNIFORMITY_INDEX).outcome()).isEqualTo(outcome);
  }

  @Test
  void aMissingBoundedParameterIsNotMeasuredAndLeavesTheResultPending() {
    FiberQualityStandard standard =
        FiberQualityStandard.builder().lengthMmMin(25.0).moisturePctMax(10.0).build();

    EvaluationResult evaluated =
        evaluate(FiberTestResult.builder().moisturePercent(5.0).build(), standard, null);

    assertThat(evaluated.approvalStatus()).isEqualTo(TestApprovalStatus.PENDING);
    assertThat(evaluated.evaluation().complete()).isFalse();
    assertThat(evaluated.evaluation().limitDefined()).isTrue();
    assertThat(evaluated.evaluation().outsideLimits()).isFalse();
    assertThat(parameter(evaluated, Parameter.LENGTH))
        .isEqualTo(
            new ParameterEvaluation(
                Parameter.LENGTH, null, 25.0, null, null, null, Outcome.NOT_MEASURED));
    assertThat(parameter(evaluated, Parameter.MOISTURE).outcome()).isEqualTo(Outcome.WITHIN_LIMITS);
  }

  @Test
  void aMissingTargetOnlyParameterIsNotMeasured() {
    FiberQualityStandard standard = FiberQualityStandard.builder().moisturePctTarget(5.0).build();

    EvaluationResult evaluated = evaluate(FiberTestResult.builder().build(), standard, null);

    assertThat(evaluated.approvalStatus()).isEqualTo(TestApprovalStatus.PENDING);
    assertThat(evaluated.evaluation().complete()).isFalse();
    assertThat(evaluated.evaluation().limitDefined()).isFalse();
    assertThat(parameter(evaluated, Parameter.MOISTURE))
        .isEqualTo(
            new ParameterEvaluation(
                Parameter.MOISTURE, null, null, null, 5.0, null, Outcome.NOT_MEASURED));
  }

  @ParameterizedTest
  @ValueSource(doubles = {28.0, 50.0})
  void strengthIsNeverComparedIntoAVerdict(double strength) {
    FiberQualityStandard standard =
        FiberQualityStandard.builder()
            .strengthCndTexMin(25.0)
            .strengthCndTexMax(30.0)
            .strengthCndTexTarget(27.0)
            .build();

    EvaluationResult evaluated =
        evaluate(FiberTestResult.builder().strengthCndTex(strength).build(), standard, "CO");

    assertThat(evaluated.approvalStatus()).isEqualTo(TestApprovalStatus.PENDING);
    assertThat(evaluated.evaluation().complete()).isFalse();
    assertThat(evaluated.evaluation().limitDefined()).isTrue();
    assertThat(evaluated.evaluation().outsideLimits()).isFalse();
    assertThat(parameter(evaluated, Parameter.STRENGTH))
        .isEqualTo(
            new ParameterEvaluation(
                Parameter.STRENGTH, strength, 25.0, 30.0, 27.0, null, Outcome.UNIT_UNCONFIRMED));
  }

  @Test
  void measuredStrengthWithNoCriterionDoesNotMakeTheEvaluationIncomplete() {
    FiberQualityStandard standard = FiberQualityStandard.builder().moisturePctMax(10.0).build();
    FiberTestResult result =
        FiberTestResult.builder().strengthCndTex(50.0).moisturePercent(7.0).build();

    EvaluationResult evaluated = evaluate(result, standard, "CO");

    assertThat(evaluated.approvalStatus()).isEqualTo(TestApprovalStatus.APPROVED);
    assertThat(evaluated.evaluation().complete()).isTrue();
    assertThat(evaluated.evaluation().limitDefined()).isTrue();
    assertThat(evaluated.evaluation().outsideLimits()).isFalse();
    assertThat(parameter(evaluated, Parameter.STRENGTH))
        .isEqualTo(
            new ParameterEvaluation(
                Parameter.STRENGTH, 50.0, null, null, null, null, Outcome.NO_LIMIT));
    assertThat(parameter(evaluated, Parameter.MOISTURE).outcome()).isEqualTo(Outcome.WITHIN_LIMITS);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "ISO_CODE,CO,REJECTED,OUTSIDE_LIMITS",
        "FIBER,CO,REJECTED,OUTSIDE_LIMITS",
        "ISO_CODE,PES,PENDING,UNIT_UNCONFIRMED",
        "FIBER,PES,PENDING,UNIT_UNCONFIRMED",
        "FIBER,BLEND,PENDING,UNIT_UNCONFIRMED"
      },
      nullValues = "BLEND")
  void finenessIsComparedOnlyForCotton(
      FiberQualityTargetType targetType,
      String isoCode,
      TestApprovalStatus status,
      Outcome outcome) {
    FiberQualityStandard standard =
        FiberQualityStandard.builder()
            .targetType(targetType)
            .finenessMax(5.0)
            .finenessTarget(4.0)
            .build();

    EvaluationResult evaluated =
        evaluate(FiberTestResult.builder().fineness(6.0).build(), standard, isoCode);

    assertThat(evaluated.approvalStatus()).isEqualTo(status);
    assertThat(parameter(evaluated, Parameter.FINENESS).outcome()).isEqualTo(outcome);
    assertThat(parameter(evaluated, Parameter.FINENESS).differenceFromTarget())
        .isEqualTo(outcome == Outcome.OUTSIDE_LIMITS ? 2.0 : null);
  }

  @Test
  void aGapDoesNotHideAReliableOutOfLimitValue() {
    FiberQualityStandard standard =
        FiberQualityStandard.builder().moisturePctMax(10.0).strengthCndTexMin(25.0).build();

    EvaluationResult evaluated =
        evaluate(FiberTestResult.builder().moisturePercent(11.0).build(), standard, null);

    assertThat(evaluated.approvalStatus()).isEqualTo(TestApprovalStatus.PENDING);
    assertThat(evaluated.evaluation().complete()).isFalse();
    assertThat(evaluated.evaluation().limitDefined()).isTrue();
    assertThat(evaluated.evaluation().outsideLimits()).isTrue();
    assertThat(evaluated.evaluation().parameters())
        .extracting(ParameterEvaluation::outcome)
        .containsExactly(Outcome.NOT_MEASURED, Outcome.OUTSIDE_LIMITS);
    assertThat(parameter(evaluated, Parameter.STRENGTH).outcome()).isEqualTo(Outcome.NOT_MEASURED);
    assertThat(parameter(evaluated, Parameter.MOISTURE).outcome())
        .isEqualTo(Outcome.OUTSIDE_LIMITS);
  }

  @Test
  void aValueInsideItsLimitsAndOffTargetIsWithinLimits() {
    FiberQualityStandard standard =
        FiberQualityStandard.builder()
            .moisturePctMin(0.0)
            .moisturePctTarget(5.0)
            .moisturePctMax(10.0)
            .build();

    EvaluationResult evaluated =
        evaluate(FiberTestResult.builder().moisturePercent(7.0).build(), standard, null);

    assertThat(evaluated.approvalStatus()).isEqualTo(TestApprovalStatus.APPROVED);
    assertThat(evaluated.evaluation().complete()).isTrue();
    assertThat(evaluated.evaluation().limitDefined()).isTrue();
    assertThat(evaluated.evaluation().outsideLimits()).isFalse();
    assertThat(parameter(evaluated, Parameter.MOISTURE))
        .isEqualTo(
            new ParameterEvaluation(
                Parameter.MOISTURE, 7.0, 0.0, 10.0, 5.0, 2.0, Outcome.WITHIN_LIMITS));
  }

  @Test
  void aTargetWithoutALimitAcceptsNothing() {
    FiberQualityStandard standard = FiberQualityStandard.builder().moisturePctTarget(5.0).build();

    EvaluationResult evaluated =
        evaluate(FiberTestResult.builder().moisturePercent(11.0).build(), standard, null);

    assertThat(evaluated.approvalStatus()).isEqualTo(TestApprovalStatus.PENDING);
    assertThat(evaluated.evaluation().complete()).isTrue();
    assertThat(evaluated.evaluation().limitDefined()).isFalse();
    assertThat(evaluated.evaluation().outsideLimits()).isFalse();
    assertThat(parameter(evaluated, Parameter.MOISTURE))
        .isEqualTo(
            new ParameterEvaluation(
                Parameter.MOISTURE, 11.0, null, null, 5.0, 6.0, Outcome.NO_LIMIT));
  }

  @Test
  void passesTheStoredSnapshotAndStoredProfileToTheSharedResolver() {
    FiberQualityStandard standard =
        FiberQualityStandard.builder()
            .standardName("Alder Beck cotton")
            .moisturePctMax(10.0)
            .build();
    standard.setId(STANDARD_ID);

    EvaluationResult evaluated =
        evaluate(FiberTestResult.builder().moisturePercent(7.0).build(), standard, "CO");

    assertThat(evaluated.evaluation().profileId()).isEqualTo(STANDARD_ID);
    assertThat(evaluated.evaluation().profileName()).isEqualTo("Alder Beck cotton");
    assertThat(evaluated.targetLabel()).isEqualTo("Cotton (100%)");
    assertThat(evaluated.evaluation().diagnosticCode()).isNull();
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
        .thenReturn(new StoredEvaluation(null, "CO 60% / PES 40%", diagnostic, null));

    EvaluationResult evaluated =
        evaluator.evaluate(
            FiberTestResult.builder().moisturePercent(7.0).build(), batch, TENANT_ID);

    assertThat(evaluated.approvalStatus()).isEqualTo(TestApprovalStatus.PENDING);
    FiberTestEvaluation evaluation = evaluated.evaluation();
    assertThat(evaluation.profileId()).isNull();
    assertThat(evaluation.profileName()).isNull();
    assertThat(evaluation.diagnosticCode()).isEqualTo(diagnostic);
    assertThat(evaluation.complete()).isFalse();
    assertThat(evaluation.limitDefined()).isFalse();
    assertThat(evaluation.outsideLimits()).isFalse();
    assertThat(evaluation.parameters())
        .containsExactly(
            new ParameterEvaluation(
                Parameter.MOISTURE, 7.0, null, null, null, null, Outcome.NO_LIMIT));
  }

  @Test
  void aMalformedSnapshotIsUnknownNeverPure() {
    when(batch.getAttributes())
        .thenReturn(Map.of(BatchCompositionSnapshot.ATTRIBUTE_KEY, Map.of("not-a-uuid", "60")));
    when(fiberQualityQueryService.evaluateStored(
            TENANT_ID, PRODUCT_ID, Optional.empty(), STANDARD_ID))
        .thenReturn(new StoredEvaluation(null, "Blend", "BATCH_COMPOSITION_UNKNOWN", null));

    assertThat(
            evaluator
                .evaluate(FiberTestResult.builder().build(), batch, TENANT_ID)
                .evaluation()
                .diagnosticCode())
        .isEqualTo("BATCH_COMPOSITION_UNKNOWN");
  }

  @Test
  void nonFiberBatchesAreSkipped() {
    when(batch.getProductType()).thenReturn(ProductType.YARN);

    EvaluationResult evaluated =
        evaluator.evaluate(FiberTestResult.builder().build(), batch, TENANT_ID);

    assertThat(evaluated.evaluation()).isNull();
    assertThat(evaluated.approvalStatus()).isEqualTo(TestApprovalStatus.PENDING);
    verify(fiberQualityQueryService, never()).evaluateStored(any(), any(), any(), any());
  }

  private EvaluationResult evaluate(
      FiberTestResult result, FiberQualityStandard standard, String isoCode) {
    when(fiberQualityQueryService.evaluateStored(
            TENANT_ID, PRODUCT_ID, Optional.of(SNAPSHOT), STANDARD_ID))
        .thenReturn(new StoredEvaluation(standard, "Cotton (100%)", null, isoCode));

    return evaluator.evaluate(result, batch, TENANT_ID);
  }

  private static ParameterEvaluation parameter(EvaluationResult result, Parameter parameter) {
    return result.evaluation().parameters().stream()
        .filter(value -> value.parameter() == parameter)
        .findFirst()
        .orElseThrow();
  }
}
