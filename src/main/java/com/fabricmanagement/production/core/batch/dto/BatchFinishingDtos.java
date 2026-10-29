package com.fabricmanagement.production.core.batch.dto;

import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingBillingBasis;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import com.fabricmanagement.production.core.batch.domain.BatchFinishingStepStatus;
import com.fabricmanagement.production.core.batch.domain.FinishingCostLineStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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
      @Schema(
              description =
                  "The tier the firm puts the colour in (dark, black, ...), which decides its price")
          UUID priceClassId,
      @Size(max = 500) String note) {}

  @Schema(
      name = "FinishingGradeMetresRequest",
      description = "Metres that came back in one quality grade")
  public record GradeMetresRequest(
      @NotNull UUID qualityGradeId, @NotNull @DecimalMin("0.001") BigDecimal metres) {}

  @Schema(name = "ReceiveLotFromFinishingRequest")
  public record ReceiveRequest(
      @NotNull @DecimalMin("0.001") BigDecimal returnedMetres,
      @Schema(
              description =
                  "The returned metres split by quality grade, each grade once; when given they"
                      + " must add up to the returned metres. Omit when the firm did not sort"
                      + " the delivery")
          @Valid
          @Size(max = 50)
          List<GradeMetresRequest> grades,
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

  @Schema(
      name = "FinishingCostSuggestionRequest",
      description = "What came back, to work out what the firm should charge")
  public record CostSuggestionRequest(
      @NotNull @DecimalMin("0.001") BigDecimal returnedMetres,
      @Schema(description = "The returned metres by quality grade; same rules as on receive")
          @Valid
          @Size(max = 50)
          List<GradeMetresRequest> grades,
      @Schema(description = "The day the prices are read for; defaults to the day the lot was sent")
          LocalDate priceDate) {}

  @Schema(name = "CancelLotFinishingRequest")
  public record CancelRequest(@NotBlank @Size(max = 500) String reason) {}

  @Schema(name = "LotFinishingStepGrade", description = "Metres a step returned in one grade")
  public record StepGradeDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID qualityGradeId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "1A")
          String qualityGradeCode,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String qualityGradeName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal metres) {}

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
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Returned metres by quality grade, best grade first; empty when the delivery was"
                      + " not sorted by grade")
          List<StepGradeDto> grades,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID colorId,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "The firm's price class for the job")
          UUID priceClassId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "DARK")
          String priceClassCode,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String priceClassName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal costAmount,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String costCurrency,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String note,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt) {}

  @Schema(name = "FinishingCostSuggestionLine", description = "One grade's share of a suggestion")
  public record CostSuggestionLineDto(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Empty when the delivery was not split: every grade together")
          UUID qualityGradeId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String qualityGradeCode,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String qualityGradeName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Metres that came back")
          BigDecimal metres,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FinishingCostLineStatus status,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID priceId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          FinishingBillingBasis billingBasis,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          FinishingChargeUnit unit,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal unitPrice,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "GBP")
          String currency,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description =
                  "The metres the price multiplies: the returned metres, or for a price on what"
                      + " was sent, the sent metres shared out by each grade's part of the return")
          BigDecimal billedMetres,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal amount) {}

  @Schema(name = "FinishingCostSuggestionTotal", description = "What the priced lines add up to")
  public record CostSuggestionTotalDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "GBP") String currency,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal amount) {}

  @Schema(
      name = "FinishingCostSuggestion",
      description =
          "What the firm should charge for the delivery by its price list. A suggestion to check"
              + " the invoice against: the amount on the step is whatever the user confirms")
  public record CostSuggestionDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate priceDate,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Every line is priced or knowingly not billed; false when a line could not be"
                      + " priced, so the total is not the whole charge")
          boolean complete,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CostSuggestionLineDto> lines,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "One total per currency; more than one when the prices differ in it")
          List<CostSuggestionTotalDto> totals) {}

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
