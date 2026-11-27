package com.fabricmanagement.production.quality.result.dto;

import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.Outcome;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.Parameter;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.ParameterEvaluation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Fibre test evaluation as recorded, unaffected by later profile edits")
public record FiberTestEvaluationDto(
    @Schema(nullable = true, description = "Identifier of the profile used") UUID profileId,
    @Schema(nullable = true, description = "Name of the profile when the result was recorded")
        String profileName,
    @Schema(nullable = true, description = "Reason code when no profile applied")
        String diagnosticCode,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "A profile applied and no parameter was unmeasured or had an unconfirmed unit")
        boolean complete,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The profile set at least one minimum or maximum")
        boolean limitDefined,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "At least one comparable measured value was outside its limits")
        boolean outsideLimits,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Measured or profiled parameters in test form order")
        List<FiberTestParameterEvaluationDto> parameters) {

  static FiberTestEvaluationDto from(FiberTestEvaluation evaluation) {
    return new FiberTestEvaluationDto(
        evaluation.profileId(),
        evaluation.profileName(),
        evaluation.diagnosticCode(),
        evaluation.complete(),
        evaluation.limitDefined(),
        evaluation.outsideLimits(),
        evaluation.parameters().stream().map(FiberTestParameterEvaluationDto::from).toList());
  }

  @Schema(description = "Recorded assessment of one fibre test parameter")
  public record FiberTestParameterEvaluationDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Parameter parameter,
      @Schema(nullable = true, description = "Measured value, when available") Double value,
      @Schema(nullable = true, description = "Minimum set by the applicable profile") Double min,
      @Schema(nullable = true, description = "Maximum set by the applicable profile") Double max,
      @Schema(nullable = true, description = "Target set by the applicable profile") Double target,
      @Schema(nullable = true, description = "Measured value minus target, when comparable")
          Double differenceFromTarget,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Outcome outcome) {

    static FiberTestParameterEvaluationDto from(ParameterEvaluation parameter) {
      return new FiberTestParameterEvaluationDto(
          parameter.parameter(),
          parameter.value(),
          parameter.min(),
          parameter.max(),
          parameter.target(),
          parameter.differenceFromTarget(),
          parameter.outcome());
    }
  }
}
