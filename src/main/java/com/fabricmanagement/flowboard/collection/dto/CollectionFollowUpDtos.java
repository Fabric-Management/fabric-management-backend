package com.fabricmanagement.flowboard.collection.dto;

import com.fabricmanagement.flowboard.collection.domain.CollectionContact.Outcome;
import com.fabricmanagement.flowboard.task.domain.TaskActionPayload;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class CollectionFollowUpDtos {
  private CollectionFollowUpDtos() {}

  @Schema(name = "CollectionFollowUpContactRequest")
  public record ContactRequest(
      @NotNull UUID operationId,
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotNull @PositiveOrZero Long expectedObligationVersion,
      @NotNull @PositiveOrZero Long expectedTaskVersion,
      @NotNull Outcome outcome,
      @NotNull Instant occurredAt,
      @Schema(nullable = true) LocalDate promisedOn,
      @DecimalMin(value = "0", inclusive = false)
          @Digits(integer = 15, fraction = 4)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(nullable = true, type = "string", pattern = "^[0-9]+(\\.[0-9]{1,4})?$")
          BigDecimal promisedAmount,
      @Schema(nullable = true) LocalDate nextFollowUpOn,
      @Schema(nullable = true) @Size(max = 2000) String note)
      implements TaskActionPayload {}

  @Schema(name = "CollectionFollowUpAssignRequest")
  public record AssignRequest(
      @NotNull UUID operationId,
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotNull @PositiveOrZero Long expectedObligationVersion,
      @NotNull @PositiveOrZero Long expectedTaskVersion,
      @NotNull UUID responsibleUserId,
      @NotBlank @Size(max = 1000) String reason)
      implements TaskActionPayload {}

  @Schema(name = "CollectionFollowUpCapability")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Capability(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              allowableValues = {"RECORD_CONTACT", "ASSIGN_RESPONSIBLE"})
          String action,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
      @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String reason,
      @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String routesTo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean needsApproval) {}

  @Schema(
      name = "CollectionFollowUpContact",
      requiredProperties = {
        "id",
        "actorId",
        "outcome",
        "occurredAt",
        "recordedAt",
        "promisedOn",
        "promisedAmount",
        "nextFollowUpOn",
        "note"
      })
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Contact(
      UUID id,
      UUID actorId,
      Outcome outcome,
      Instant occurredAt,
      Instant recordedAt,
      @Schema(nullable = true) LocalDate promisedOn,
      @Schema(type = "string", nullable = true) String promisedAmount,
      @Schema(nullable = true) LocalDate nextFollowUpOn,
      @Schema(nullable = true) String note) {}

  @Schema(
      name = "CollectionFollowUpDetail",
      requiredProperties = {
        "id",
        "obligationId",
        "episode",
        "version",
        "obligationVersion",
        "taskId",
        "taskVersion",
        "currency",
        "expectedAmount",
        "recordedOutstanding",
        "missingInstrumentAmount",
        "phase",
        "sourceMissing",
        "stale",
        "checkedAt",
        "actions",
        "responsibleUserId",
        "responsibleDisplayName",
        "partnerId",
        "customerLabel",
        "agreedDate",
        "dueDate",
        "promisedOn",
        "promisedAmount",
        "nextFollowUpOn",
        "closedAt",
        "overdueDays"
      })
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Detail(
      UUID id,
      UUID obligationId,
      int episode,
      long version,
      long obligationVersion,
      UUID taskId,
      long taskVersion,
      @Schema(nullable = true) UUID responsibleUserId,
      @Schema(nullable = true) String responsibleDisplayName,
      UUID partnerId,
      @Schema(nullable = true) String customerLabel,
      String currency,
      @Schema(type = "string") String expectedAmount,
      @Schema(type = "string") String recordedOutstanding,
      @Schema(type = "string") String missingInstrumentAmount,
      @Schema(allowableValues = {"COLLECT_PAYMENT", "RECEIVE_CHEQUE", "CLEAR_CHEQUE"}) String phase,
      @Schema(nullable = true) LocalDate agreedDate,
      @Schema(nullable = true) LocalDate dueDate,
      @Schema(nullable = true) LocalDate promisedOn,
      @Schema(type = "string", nullable = true) String promisedAmount,
      @Schema(nullable = true) LocalDate nextFollowUpOn,
      @Schema(nullable = true) Instant closedAt,
      @Schema(nullable = true, minimum = "0") Long overdueDays,
      boolean sourceMissing,
      boolean stale,
      Instant checkedAt,
      List<Capability> actions) {}

  @Schema(
      name = "CollectionFollowUpResult",
      requiredProperties = {"followUpId", "version", "replayed", "contactId"})
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Result(
      UUID followUpId, long version, @Schema(nullable = true) UUID contactId, boolean replayed) {}
}
