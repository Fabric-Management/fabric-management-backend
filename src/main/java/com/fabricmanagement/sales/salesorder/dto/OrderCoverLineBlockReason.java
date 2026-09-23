package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.dto.OrderCoverDetail.DecisionBlockedReasonCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "OrderCoverLineBlockReason")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrderCoverLineBlockReason(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Code code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> incompleteReasons,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        DecisionBlockedReasonCode caseReasonCode) {
  public OrderCoverLineBlockReason {
    incompleteReasons = List.copyOf(incompleteReasons == null ? List.of() : incompleteReasons);
  }

  @Schema(name = "OrderCoverLineBlockReasonCode")
  public enum Code {
    LINE_NOT_OPEN,
    NO_EVIDENCE,
    REQUIREMENT_COMPLETENESS_UNKNOWN,
    REQUIREMENT_INCOMPLETE,
    LINE_ALREADY_FULFILLED,
    ACTIVE_RESERVATION_EXISTS,
    ACTIVE_PRODUCTION_EXISTS,
    ACTION_NOT_ALLOWED
  }
}
