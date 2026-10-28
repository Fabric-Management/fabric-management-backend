package com.fabricmanagement.production.core.batch.dto;

import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.production.core.batch.domain.BatchFinishingStepStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Finishing history of a fabric lot: what was done to it, where, and what came back. */
public final class BatchFinishingDtos {

  private BatchFinishingDtos() {}

  @Schema(
      name = "SendLotForFinishingRequest",
      description = "The whole lot goes out; its current metres are what was sent")
  public record SendRequest(
      @NotNull UUID processId,
      @Schema(description = "The subcontractor; omit when the step is done at an own site")
          UUID partnerId,
      @Schema(description = "The number the firm gave the job") @Size(max = 100)
          String firmJobNumber,
      @Schema(description = "Defaults to today") LocalDate sentOn,
      @Schema(description = "Required for dyeing and printing, not allowed for other processes")
          UUID colorId,
      @Size(max = 500) String note) {}

  @Schema(name = "ReceiveLotFromFinishingRequest")
  public record ReceiveRequest(
      @NotNull @DecimalMin("0.001") BigDecimal returnedMetres,
      @Schema(description = "Defaults to today") LocalDate returnedOn,
      @Schema(description = "Set or corrected now that the firm has invoiced or confirmed it")
          @Size(max = 100)
          String firmJobNumber,
      @DecimalMin("0") BigDecimal costAmount,
      @Schema(description = "Three-letter currency code; required with a cost")
          @Pattern(regexp = "[A-Za-z]{3}")
          String costCurrency,
      @Size(max = 500) String note) {}

  @Schema(
      name = "RecordFinishedLotStepRequest",
      description = "A step finished before the lot came to us, entered as done")
  public record RecordDoneRequest(
      @NotNull UUID processId,
      UUID partnerId,
      @Size(max = 100) String firmJobNumber,
      @NotNull @DecimalMin("0.001") BigDecimal sentMetres,
      @NotNull LocalDate sentOn,
      @NotNull @DecimalMin("0.001") BigDecimal returnedMetres,
      @NotNull LocalDate returnedOn,
      UUID colorId,
      @DecimalMin("0") BigDecimal costAmount,
      @Pattern(regexp = "[A-Za-z]{3}") String costCurrency,
      @Size(max = 500) String note) {}

  @Schema(name = "CancelLotFinishingRequest")
  public record CancelRequest(@NotBlank @Size(max = 500) String reason) {}

  @Schema(name = "LotFinishingStep")
  public record StepDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID batchId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String batchCode,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID productId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Order on the lot, from 1")
          int sequence,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID processId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String processCode,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String processName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FabricFinishingCategory category,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Null: an own site")
          UUID partnerId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String firmJobNumber,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BatchFinishingStepStatus status,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal sentMetres,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate sentOn,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          BigDecimal returnedMetres,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDate returnedOn,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Sent minus returned; negative when the fabric came back longer")
          BigDecimal lossMetres,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID colorId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal costAmount,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String costCurrency,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String note,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt) {}

  @Schema(name = "LotFinishingHistory")
  public record HistoryDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID batchId,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "The lot is out for a step and not for sale")
          boolean out,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Categories of the done steps, in the order they first happened")
          List<FabricFinishingCategory> appliedCategories,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<StepDto> steps) {}
}
