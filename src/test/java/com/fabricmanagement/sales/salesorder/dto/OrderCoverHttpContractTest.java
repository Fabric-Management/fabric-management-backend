package com.fabricmanagement.sales.salesorder.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.flowboard.task.dto.DecisionTransitionRequest;
import com.fabricmanagement.flowboard.task.dto.DecisionTransitionResult;
import io.swagger.v3.core.converter.ModelConverters;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderCoverHttpContractTest {
  @Test
  void commandDetailAndReceiptSchemasExportEveryContractPropertyAsRequired() {
    var schemas = new java.util.LinkedHashMap<String, io.swagger.v3.oas.models.media.Schema>();
    schemas.putAll(ModelConverters.getInstance().readAll(DecisionTransitionRequest.class));
    schemas.putAll(ModelConverters.getInstance().readAll(DecisionTransitionResult.class));
    schemas.putAll(ModelConverters.getInstance().readAll(OrderCoverDetail.class));
    schemas.putAll(ModelConverters.getInstance().readAll(OrderCoverResultDto.class));
    schemas.putAll(ModelConverters.getInstance().readAll(ConfirmProductionCoverPayload.class));
    schemas.putAll(ModelConverters.getInstance().readAll(OrderCoverSelectionPreviewRequest.class));
    schemas.putAll(ModelConverters.getInstance().readAll(OrderCoverSelectionPreview.class));
    assertRequired(
        schemas,
        "DecisionTransitionRequest",
        "action",
        "expectedVersion",
        "idempotencyKey",
        "payload");
    assertRequired(
        schemas,
        "DecisionTransitionResult",
        "result",
        "taskId",
        "taskVersion",
        "taskState",
        "remainingLineIds",
        "replayed");
    assertRequired(
        schemas,
        "OrderCoverCase",
        "id",
        "salesOrderId",
        "revision",
        "state",
        "taskId",
        "taskVersion",
        "unresolvedLineIds",
        "latestEvidenceId");
    assertRequired(
        schemas,
        "OrderCoverDetail",
        "case",
        "subject",
        "assignment",
        "evidence",
        "actions",
        "lines",
        "results");
    assertRequired(
        schemas,
        "OrderCoverLineDecision",
        "lineId",
        "lineNumber",
        "label",
        "unit",
        "evidenceId",
        "evidenceRevision",
        "selectable",
        "blockReason",
        "productionQuantity",
        "rationaleRequiredIfSelected");
    assertRequired(
        schemas, "OrderCoverLineBlockReason", "code", "incompleteReasons", "caseReasonCode");
    assertRequired(
        schemas, "OrderCoverSelectionPreviewRequest", "evidenceId", "evidenceRevision", "lineIds");
    assertRequired(
        schemas,
        "OrderCoverSelectionPreview",
        "accepted",
        "rejection",
        "lines",
        "rationaleRequired");
    assertRequired(
        schemas,
        "OrderCoverSelectionPreviewRejection",
        "code",
        "lineId",
        "incompleteReasons",
        "caseReasonCode");
    assertRequired(
        schemas,
        "OrderCoverSelectionPreviewLine",
        "lineId",
        "lineNumber",
        "label",
        "productionQuantity");
    assertRequired(
        schemas,
        "ConfirmProductionCoverPayload",
        "caseId",
        "evidenceId",
        "evidenceRevision",
        "lineIds",
        "rationale",
        "note");
    assertRequired(
        schemas,
        "OrderCoverResult",
        "id",
        "caseId",
        "caseRevision",
        "actorId",
        "actorKind",
        "policyKey",
        "rationale",
        "recordedAt",
        "evidenceId",
        "evidenceRevision",
        "lines",
        "supersedesResultId");
    assertRequired(
        schemas,
        "OrderCoverLineResult",
        "lineId",
        "outcome",
        "quantity",
        "suitabilityAtDecision",
        "requirementProfileId",
        "requirementProfileVersion",
        "downstream");
  }

  private static void assertRequired(
      java.util.Map<String, io.swagger.v3.oas.models.media.Schema> schemas,
      String name,
      String... properties) {
    var schema = schemas.get(name);
    assertThat(schema).as(name).isNotNull();
    assertThat(schema.getRequired()).containsExactlyInAnyOrderElementsOf(List.of(properties));
  }
}
