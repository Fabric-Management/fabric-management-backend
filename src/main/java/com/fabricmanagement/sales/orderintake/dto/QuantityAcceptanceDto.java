package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.sales.orderintake.domain.AcceptanceChannel;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptance;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptanceBasis;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptanceStatus;
import com.fabricmanagement.sales.orderintake.domain.RemainingNeed;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A recorded stock choice for a line (SOI D3). {@code coversCurrentTerms} is false once the line's
 * product, colour, width, unit, quantity, single-lot condition or price changed: the customer must
 * accept again. {@code conditional} means the lots' tone compatibility is still open.
 */
@Schema(name = "QuantityAcceptance")
public record QuantityAcceptanceDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderLineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID proposalId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String optionKey,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuantityOption.OptionKind optionKind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuantityOption.Compatibility compatibility,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean conditional,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal acceptedQuantity,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unit,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> pieceIds,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> batchIds,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuantityAcceptanceBasis basis,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean remnantAcknowledged,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        RemainingNeed remainingNeed,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        BigDecimal remainingQuantity,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String customerContact,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) AcceptanceChannel channel,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant acceptedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String evidenceNote,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID evidenceAttachmentId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean customerStatementConfirmed,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuantityAcceptanceStatus status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean coversCurrentTerms,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "Latest compatibility request status for a conditional acceptance")
        String compatibilityRequestStatus,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID recordedBy,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt) {

  public static QuantityAcceptanceDto from(
      QuantityAcceptance value, String currentTerms, String compatibilityRequestStatus) {
    return new QuantityAcceptanceDto(
        value.getId(),
        value.getSalesOrderLineId(),
        value.getProposalId(),
        value.getOptionKey(),
        value.getOptionKind(),
        value.getCompatibility(),
        value.isConditional(),
        value.getAcceptedQty(),
        value.getUnit(),
        List.copyOf(value.getPieceIds()),
        List.copyOf(value.getBatchIds()),
        value.getBasis(),
        value.isRemnantAcknowledged(),
        value.getRemainingNeed(),
        value.getRemainingQty(),
        value.getCustomerContact(),
        value.getChannel(),
        value.getAcceptedAt(),
        value.getEvidenceNote(),
        value.getEvidenceAttachmentId(),
        value.isCustomerStatementConfirmed(),
        value.getStatus(),
        value.covers(currentTerms),
        compatibilityRequestStatus,
        value.getRecordedBy(),
        value.getRecordedAt());
  }
}
