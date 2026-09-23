package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.dto.OrderCoverDetail.DecisionBlockedReasonCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "OrderCoverSelectionPreview")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrderCoverSelectionPreview(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean accepted,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Rejection rejection,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Line> lines,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean rationaleRequired) {
  public OrderCoverSelectionPreview {
    lines = List.copyOf(lines);
  }

  public static OrderCoverSelectionPreview rejected(
      OrderCoverLineBlockReason.Code code,
      UUID lineId,
      List<String> incompleteReasons,
      DecisionBlockedReasonCode caseReasonCode) {
    return new OrderCoverSelectionPreview(
        false, new Rejection(code, lineId, incompleteReasons, caseReasonCode), List.of(), false);
  }

  @Schema(name = "OrderCoverSelectionPreviewRejection")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Rejection(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderCoverLineBlockReason.Code code,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID lineId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> incompleteReasons,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          DecisionBlockedReasonCode caseReasonCode) {
    public Rejection {
      incompleteReasons = List.copyOf(incompleteReasons == null ? List.of() : incompleteReasons);
    }
  }

  @Schema(name = "OrderCoverSelectionPreviewLine")
  public record Line(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID lineId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int lineNumber,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          OrderCoverEvidenceDto.Quantity productionQuantity) {}
}
