package com.fabricmanagement.production.quality.result.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** The measurements and acceptance criteria as they were at the time of recording. */
public record FiberTestEvaluation(
    UUID profileId,
    String profileName,
    String diagnosticCode,
    boolean complete,
    boolean limitDefined,
    boolean outsideLimits,
    List<ParameterEvaluation> parameters) {

  public FiberTestEvaluation {
    parameters = List.copyOf(parameters);
  }

  @Schema(name = "FiberTestEvaluationParameter", enumAsRef = true)
  public enum Parameter {
    FINENESS,
    LENGTH,
    STRENGTH,
    ELONGATION,
    MOISTURE,
    TRASH_CONTENT,
    UNIFORMITY_INDEX
  }

  @Schema(name = "FiberTestEvaluationOutcome", enumAsRef = true)
  public enum Outcome {
    NOT_MEASURED,
    UNIT_UNCONFIRMED,
    OUTSIDE_LIMITS,
    WITHIN_LIMITS,
    NO_LIMIT
  }

  public record ParameterEvaluation(
      Parameter parameter,
      Double value,
      Double min,
      Double max,
      Double target,
      Double differenceFromTarget,
      Outcome outcome) {}
}
