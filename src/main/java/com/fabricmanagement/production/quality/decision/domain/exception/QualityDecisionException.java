package com.fabricmanagement.production.quality.decision.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;

public class QualityDecisionException extends ProductionDomainException {

  private QualityDecisionException(String message, String code, int status) {
    super(message, code, status);
  }

  public static QualityDecisionException noUnits() {
    return new QualityDecisionException(
        "Batch has no eligible StockUnits for a quality decision", "QC_DECISION_NO_UNITS", 422);
  }

  public static QualityDecisionException unitMismatch() {
    return new QualityDecisionException(
        "Selected StockUnits must be active, eligible, tenant-owned, and belong to the batch",
        "QC_DECISION_UNIT_MISMATCH",
        422);
  }

  public static QualityDecisionException populationDrift() {
    return new QualityDecisionException(
        "Quality decision population changed while the decision was being applied",
        "QC_DECISION_POPULATION_DRIFT",
        409);
  }

  public static QualityDecisionException batchActive(String status) {
    return new QualityDecisionException(
        "Quality decision is not allowed for active or terminal batch status " + status,
        "QC_DECISION_BATCH_ACTIVE",
        409);
  }

  public static QualityDecisionException reasonInvalid() {
    return new QualityDecisionException(
        "Reason code is not valid for the requested outcome and origin",
        "QC_DECISION_REASON_INVALID",
        422);
  }

  public static QualityDecisionException remarksRequired() {
    return new QualityDecisionException(
        "Remarks are required when reasonCode is OTHER", "QC_DECISION_REMARKS_REQUIRED", 422);
  }

  public static QualityDecisionException commandInvalid(String message) {
    return new QualityDecisionException(message, "QC_DECISION_COMMAND_INVALID", 422);
  }

  public static QualityDecisionException supersedesInvalid() {
    return new QualityDecisionException(
        "supersedesDecisionId must identify an earlier decision for the same batch",
        "QC_DECISION_SUPERSEDES_INVALID",
        422);
  }

  public static QualityDecisionException actorMissing() {
    return new QualityDecisionException(
        "A trusted decision actor is required", "QC_DECISION_ACTOR_REQUIRED", 500);
  }

  public static QualityDecisionException sourceEventConflict() {
    return new QualityDecisionException(
        "sourceEventId is already bound to a different quality decision",
        "QC_DECISION_SOURCE_EVENT_CONFLICT",
        409);
  }
}
