package com.fabricmanagement.sales.orderintake.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "DraftStockSelection")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DraftStockSelectionDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID selectionId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "No provisional session on a direct quote-to-order transfer")
        UUID sessionId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID clientLineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long selectionRevision,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) State state,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Policy policy,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant expiresAt,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Milliseconds remaining by the server clock; zero without live ownership")
        long remainingMs,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        QuantityProposalDto.Option option,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Secret periods of this session only; never published in stock listings")
        List<Grant> grants,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Capability> capabilities,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID salesOrderId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID salesOrderLineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long orderVersion) {
  @Schema(name = "DraftStockSelectionState", enumAsRef = true)
  public enum State {
    OPEN,
    HELD,
    BLOCKED,
    EXPIRED,
    RELEASED,
    TRANSFERRED,
    CONVERTED,
    SUPERSEDED
  }

  @Schema(name = "DraftStockSelectionAction", enumAsRef = true)
  public enum Action {
    ACQUIRE,
    APPLY,
    RENEW,
    RELEASE,
    CLOSE
  }

  @Schema(name = "DraftStockSelectionReason", enumAsRef = true)
  public enum Reason {
    OWNERSHIP_REQUIRED,
    SELECTION_EXPIRED,
    SELECTION_ENDED
  }

  @Schema(name = "DraftStockSelectionCapability")
  public record Capability(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Action action,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Reason reason,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String routesTo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean needsApproval,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean needsConfirmation) {}

  @Schema(name = "DraftStockSelectionPolicy")
  public record Policy(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long leaseSeconds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long renewAfterSeconds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long idleAfterSeconds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long idleWarningSeconds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int maxKeysPerRequest) {}

  @Schema(name = "DraftStockSelectionGrant")
  public record Grant(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID stockUnitId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID token,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt) {}
}
