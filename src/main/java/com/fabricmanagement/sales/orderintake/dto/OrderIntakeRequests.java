package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.sales.orderintake.domain.AcceptanceChannel;
import com.fabricmanagement.sales.orderintake.domain.RemainingNeed;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request bodies of the order-intake commands (SOI D2, D3). */
public final class OrderIntakeRequests {

  private OrderIntakeRequests() {}

  @Schema(name = "RecordCustomerToneAcceptanceRequest")
  public record RecordToneAcceptance(
      @NotNull
          @Schema(
              description = "The order line whose concrete tone difference the customer accepted")
          UUID salesOrderLineId,
      @NotNull @Size(min = 2, max = 20) List<@NotNull UUID> batchIds,
      @NotBlank @Size(max = 2000) String evidenceNote,
      UUID evidenceAttachmentId,
      @NotBlank @Size(max = 200) String customerContact,
      @NotNull AcceptanceChannel channel,
      @NotNull @PastOrPresent Instant acceptedAt,
      @jakarta.validation.constraints.AssertTrue
          @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "The recorder received the customer's acceptance of the shown tone difference")
          boolean customerStatementConfirmed) {}

  @Schema(name = "RecordQuantityAcceptanceRequest")
  public record RecordQuantityAcceptance(
      @NotNull UUID proposalId,
      @NotBlank @Size(max = 80) String optionKey,
      @Schema(description = "The recorder confirmed the single-piece remnant warning (SOI A01)")
          boolean remnantAcknowledged,
      @Schema(description = "Required when the option is below the request")
          RemainingNeed remainingNeed,
      @Size(max = 200) String customerContact,
      AcceptanceChannel channel,
      @PastOrPresent Instant acceptedAt,
      @Size(max = 2000) String evidenceNote,
      UUID evidenceAttachmentId,
      @Schema(description = "\"I received this acceptance from the customer\" (SOI A11)")
          boolean customerStatementConfirmed,
      @Schema(description = "Client key; repeating it returns the first result") @Size(max = 100)
          String idempotencyKey) {}
}
