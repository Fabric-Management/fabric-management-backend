package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.sales.orderintake.domain.AcceptanceChannel;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestDecisionOutcome;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestEvaluationOutcome;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestRevisionStatus;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestStatus;
import com.fabricmanagement.sales.orderintake.domain.IntakeAttachmentKind;
import com.fabricmanagement.sales.orderintake.domain.PartialDeliveryPreference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contracts of custom customer requests, sample approval and delivery preference (SOI D7). */
public final class CustomerRequestDtos {

  private CustomerRequestDtos() {}

  @Schema(name = "CustomerProductRequestInput")
  public record RequestInput(
      @Size(max = 4000) String description,
      UUID referenceProductId,
      @DecimalMin("0.001") BigDecimal requestedQty,
      @Size(max = 20) String unit,
      @Size(max = 500) String requestedColorNote,
      @DecimalMin("0.01") BigDecimal requestedWidth,
      @Pattern(regexp = "CM|IN|cm|in") String requestedWidthUnit,
      LocalDate requestedDeliveryDate,
      @PastOrPresent Instant sampleReceivedAt,
      @Size(max = 4000) String sampleNote,
      @Schema(description = "Files already uploaded to this order to link to the request")
          List<UUID> attachmentIds) {}

  @Schema(name = "EvaluateCustomerProductRequest")
  public record EvaluateRequest(
      @NotNull CustomerRequestEvaluationOutcome outcome, @NotBlank @Size(max = 4000) String note) {}

  @Schema(name = "ProposeCustomerRequestRevision")
  public record ProposeRevision(
      @NotNull CustomerRequestEvaluationOutcome solution,
      @NotNull UUID productId,
      @NotBlank @Size(max = 4000) String summary,
      @Size(max = 4000) String counterSampleNote) {}

  @Schema(name = "RecordCustomerRequestDecision")
  public record RecordDecision(
      @NotNull CustomerRequestDecisionOutcome outcome,
      @NotBlank @Size(max = 200) String customerContact,
      @NotNull AcceptanceChannel channel,
      @NotNull @PastOrPresent Instant decidedAt,
      @Size(max = 4000) String note,
      UUID evidenceAttachmentId,
      @Schema(description = "\"I received this answer from the customer\" (SOI A11)")
          boolean customerStatementConfirmed,
      @Schema(
              description =
                  "Required after resolution: version of the final line shown to the customer")
          Long expectedLineVersion) {}

  @Schema(name = "ResolveCustomerProductRequest")
  public record ResolveRequest(
      UUID colorId,
      @DecimalMin("0.01") BigDecimal finishedWidth,
      @Pattern(regexp = "CM|IN|cm|in") String finishedWidthUnit,
      @DecimalMin("0") BigDecimal unitPrice,
      @Size(min = 3, max = 3) String currency,
      LocalDate requestedDeliveryDate) {}

  @Schema(name = "RecordPartialDeliveryPreference")
  public record RecordDeliveryPreference(
      @NotNull PartialDeliveryPreference preference,
      @Size(max = 200) String customerContact,
      AcceptanceChannel channel,
      @PastOrPresent Instant decidedAt) {}

  @Schema(name = "IntakeAttachment")
  public record AttachmentDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID salesOrderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID requestId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) IntakeAttachmentKind kind,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fileName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String contentType,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sizeBytes,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID uploadedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant uploadedAt) {}

  @Schema(name = "CustomerRequestEvaluation")
  public record EvaluationDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CustomerRequestEvaluationOutcome outcome,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String note,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID evaluatedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant evaluatedAt) {}

  @Schema(name = "CustomerRequestDecision")
  public record DecisionDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID revisionId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CustomerRequestDecisionOutcome outcome,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String customerContact,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AcceptanceChannel channel,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant decidedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String note,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          UUID evidenceAttachmentId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean customerStatementConfirmed,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID recordedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt) {}

  @Schema(name = "CustomerRequestRevision")
  public record RevisionDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revisionNo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          CustomerRequestEvaluationOutcome solution,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID productId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String summary,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          String counterSampleNote,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CustomerRequestRevisionStatus status,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID proposedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant proposedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant sentAt) {}

  @Schema(name = "CustomerProductRequest")
  public record RequestDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String uid,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID customerId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID salesOrderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID originOrderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String description,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID referenceProductId,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Null means not known yet, never zero (SOI N01)")
          BigDecimal requestedQty,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String unit,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          String requestedColorNote,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          BigDecimal requestedWidth,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          String requestedWidthUnit,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          LocalDate requestedDeliveryDate,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          Instant sampleReceivedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String sampleNote,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CustomerRequestStatus status,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int currentRevisionNo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID resolvedLineId,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "The latest customer approval covers the current terms")
          boolean approvalCoversCurrentTerms,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RevisionDto> revisions,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DecisionDto> decisions,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<EvaluationDto> evaluations,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AttachmentDto> attachments,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID recordedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt) {}

  @Schema(name = "PartialDeliveryPreferenceView")
  public record DeliveryPreferenceDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PartialDeliveryPreference preference,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String customerContact,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          AcceptanceChannel channel,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant decidedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID recordedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant recordedAt) {}
}
