package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.sales.orderintake.domain.AcceptanceChannel;
import com.fabricmanagement.sales.orderintake.domain.CustomerToneAcceptance;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "CustomerToneAcceptance")
public record CustomerToneAcceptanceDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID customerId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderLineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> batchIds,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String evidenceNote,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID evidenceAttachmentId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String customerContact,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AcceptanceChannel channel,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant acceptedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean customerStatementConfirmed,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID recordedBy) {

  public static CustomerToneAcceptanceDto from(CustomerToneAcceptance value) {
    return new CustomerToneAcceptanceDto(
        value.getId(),
        value.getCustomerId(),
        value.getSalesOrderLineId(),
        value.getBatchIds(),
        value.getEvidenceNote(),
        value.getEvidenceAttachmentId(),
        value.getCustomerContact(),
        value.getChannel(),
        value.getAcceptedAt(),
        value.isCustomerStatementConfirmed(),
        value.getRecordedBy());
  }
}
