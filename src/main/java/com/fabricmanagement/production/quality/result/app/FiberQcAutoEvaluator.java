package com.fabricmanagement.production.quality.result.app;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService;
import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchCompositionSnapshot;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.Outcome;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.Parameter;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.ParameterEvaluation;
import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evaluates the recorded measurements against the profile applicable to the lot's snapshot. */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberQcAutoEvaluator {

  private static final Limits NO_CRITERIA = new Limits(null, null, null);

  // Keep this order aligned with the fibre test form.
  private static final List<ParameterInput> INPUTS =
      List.of(
          new ParameterInput(
              Parameter.FINENESS,
              FiberTestResult::getFineness,
              standard ->
                  new Limits(
                      standard.getFinenessMin(),
                      standard.getFinenessMax(),
                      standard.getFinenessTarget())),
          new ParameterInput(
              Parameter.LENGTH,
              FiberTestResult::getLengthMm,
              standard ->
                  new Limits(
                      standard.getLengthMmMin(),
                      standard.getLengthMmMax(),
                      standard.getLengthMmTarget())),
          new ParameterInput(
              Parameter.STRENGTH,
              FiberTestResult::getStrengthCndTex,
              standard ->
                  new Limits(
                      standard.getStrengthCndTexMin(),
                      standard.getStrengthCndTexMax(),
                      standard.getStrengthCndTexTarget())),
          new ParameterInput(
              Parameter.ELONGATION,
              FiberTestResult::getElongationPercent,
              standard ->
                  new Limits(
                      standard.getElongationPctMin(),
                      standard.getElongationPctMax(),
                      standard.getElongationPctTarget())),
          new ParameterInput(
              Parameter.MOISTURE,
              FiberTestResult::getMoisturePercent,
              standard ->
                  new Limits(
                      standard.getMoisturePctMin(),
                      standard.getMoisturePctMax(),
                      standard.getMoisturePctTarget())),
          new ParameterInput(
              Parameter.TRASH_CONTENT,
              FiberTestResult::getTrashContentPercent,
              standard ->
                  new Limits(
                      standard.getTrashContentPctMin(),
                      standard.getTrashContentPctMax(),
                      standard.getTrashContentPctTarget())),
          new ParameterInput(
              Parameter.UNIFORMITY_INDEX,
              FiberTestResult::getUniformityIndex,
              standard ->
                  new Limits(
                      standard.getUniformityIndexMin(),
                      standard.getUniformityIndexMax(),
                      standard.getUniformityIndexTarget())));

  private final FiberQualityQueryService fiberQualityQueryService;

  public record EvaluationResult(FiberTestEvaluation evaluation, String targetLabel) {

    public TestApprovalStatus approvalStatus() {
      if (evaluation == null || !evaluation.complete() || !evaluation.limitDefined()) {
        return TestApprovalStatus.PENDING;
      }
      return evaluation.outsideLimits() ? TestApprovalStatus.REJECTED : TestApprovalStatus.APPROVED;
    }
  }

  @Transactional(readOnly = true)
  public EvaluationResult evaluate(FiberTestResult result, Batch batch, UUID tenantId) {
    if (batch.getProductType() != ProductType.FIBER) {
      log.debug("Skipping QC auto-evaluation: batch productType={}", batch.getProductType());
      return new EvaluationResult(null, null);
    }

    var resolved =
        fiberQualityQueryService.evaluateStored(
            tenantId,
            batch.getProductId(),
            BatchCompositionSnapshot.read(batch.getAttributes()),
            batch.getQualityStandardId());
    FiberQualityStandard profile = resolved.standard();
    List<ParameterEvaluation> parameters =
        INPUTS.stream()
            .map(input -> evaluateParameter(input, result, profile, resolved.targetIsoCode()))
            .filter(Objects::nonNull)
            .toList();

    if (profile == null) {
      return new EvaluationResult(
          new FiberTestEvaluation(
              null, null, resolved.diagnosticCode(), false, false, false, parameters),
          resolved.targetLabel());
    }

    boolean complete =
        parameters.stream()
            .noneMatch(
                parameter ->
                    parameter.outcome() == Outcome.NOT_MEASURED
                        || parameter.outcome() == Outcome.UNIT_UNCONFIRMED);
    boolean limitDefined =
        parameters.stream()
            .anyMatch(parameter -> parameter.min() != null || parameter.max() != null);
    boolean outsideLimits =
        parameters.stream().anyMatch(parameter -> parameter.outcome() == Outcome.OUTSIDE_LIMITS);
    return new EvaluationResult(
        new FiberTestEvaluation(
            profile.getId(),
            profile.getStandardName(),
            null,
            complete,
            limitDefined,
            outsideLimits,
            parameters),
        resolved.targetLabel());
  }

  private static ParameterEvaluation evaluateParameter(
      ParameterInput input, FiberTestResult result, FiberQualityStandard profile, String isoCode) {
    Double value = input.measurement().apply(result);
    Limits limits = profile == null ? NO_CRITERIA : input.limits().apply(profile);
    if (value == null && !limits.hasCriterion()) {
      return null;
    }
    boolean comparable = isComparable(input.parameter(), isoCode);
    Double difference =
        comparable && value != null && limits.target() != null ? value - limits.target() : null;
    return new ParameterEvaluation(
        input.parameter(),
        value,
        limits.min(),
        limits.max(),
        limits.target(),
        difference,
        outcome(value, limits, comparable));
  }

  private static boolean isComparable(Parameter parameter, String isoCode) {
    return switch (parameter) {
      case FINENESS -> "CO".equals(isoCode);
      case STRENGTH -> false;
      case LENGTH, ELONGATION, MOISTURE, TRASH_CONTENT, UNIFORMITY_INDEX -> true;
    };
  }

  private static Outcome outcome(Double value, Limits limits, boolean comparable) {
    // Unmentioned parameters have already been omitted.
    if (value == null) {
      return Outcome.NOT_MEASURED;
    }
    if (!comparable && limits.hasCriterion()) {
      return Outcome.UNIT_UNCONFIRMED;
    }
    if (!limits.hasBound()) {
      return Outcome.NO_LIMIT;
    }
    if ((limits.min() != null && value < limits.min())
        || (limits.max() != null && value > limits.max())) {
      return Outcome.OUTSIDE_LIMITS;
    }
    return Outcome.WITHIN_LIMITS;
  }

  private record ParameterInput(
      Parameter parameter,
      Function<FiberTestResult, Double> measurement,
      Function<FiberQualityStandard, Limits> limits) {}

  private record Limits(Double min, Double max, Double target) {

    boolean hasBound() {
      return min != null || max != null;
    }

    boolean hasCriterion() {
      return hasBound() || target != null;
    }
  }
}
