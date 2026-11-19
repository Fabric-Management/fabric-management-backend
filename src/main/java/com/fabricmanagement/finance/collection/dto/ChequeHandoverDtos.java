package com.fabricmanagement.finance.collection.dto;

import com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeState;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind;
import com.fabricmanagement.finance.collection.dto.CollectionRequests.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

public final class ChequeHandoverDtos {
  public static final int MAX_ROWS = 50;
  public static final int MAX_APPLICATIONS = 500;

  private ChequeHandoverDtos() {}

  @Schema(name = "FinanceCollectionChequeHandoverSaveRequest")
  public record SaveRequest(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID operationId,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID partnerId,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate receivedOn,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
      @NotBlank
          @Size(max = 1000)
          @Schema(maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
          String reason,
      @NotBlank
          @Size(max = 1000)
          @Schema(maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
          String evidence,
      @NotNull
          @Size(max = MAX_APPLICATIONS)
          @ArraySchema(
              maxItems = MAX_APPLICATIONS,
              arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
              schema = @Schema(implementation = Version.class))
          List<@NotNull @Valid Version> obligationVersions,
      @NotNull
          @Size(min = 1, max = MAX_ROWS)
          @ArraySchema(
              minItems = 1,
              maxItems = MAX_ROWS,
              arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
              schema = @Schema(implementation = SaveRowInput.class))
          List<@NotNull @Valid SaveRowInput> rows) {}

  @Schema(name = "FinanceCollectionChequeHandoverSaveRowInput")
  public record SaveRowInput(
      @NotBlank
          @Size(max = 100)
          @Schema(maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
          String rowKey,
      @NotBlank
          @Size(max = 100)
          @Schema(maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
          String instrumentReference,
      @NotBlank
          @Pattern(regexp = "[A-Z]{3}")
          @Schema(
              pattern = "[A-Z]{3}",
              example = "GBP",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String currency,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(type = "string", example = "1000.00", requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal amount,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate dueOn,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DrawerKind drawerKind,
      @Schema(nullable = true) UUID drawerId,
      @Size(max = 200) @Schema(nullable = true, maxLength = 200) String drawerName,
      @NotNull
          @Size(max = MAX_APPLICATIONS)
          @ArraySchema(
              maxItems = MAX_APPLICATIONS,
              arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
              schema = @Schema(implementation = SaveApplication.class))
          List<@NotNull @Valid SaveApplication> applications) {}

  @Schema(name = "FinanceCollectionChequeHandoverSaveApplication")
  public record SaveApplication(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID obligationId,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(type = "string", example = "1000.00", requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal amount) {}

  @Schema(name = "FinanceCollectionChequeHandoverRefreshRequest")
  public record RefreshRequest(
      @NotNull
          @Size(min = 1, max = MAX_APPLICATIONS)
          @ArraySchema(
              minItems = 1,
              maxItems = MAX_APPLICATIONS,
              arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
              schema = @Schema(type = "string", format = "uuid"))
          List<@NotNull UUID> obligationIds) {}

  @Schema(
      name = "FinanceCollectionChequeHandoverTargetRefreshDto",
      requiredProperties = {"orderId", "status"})
  public record TargetRefresh(UUID orderId, RefreshStatus status) {}

  @Schema(name = "FinanceCollectionChequeHandoverRefreshStatus", enumAsRef = true)
  public enum RefreshStatus {
    REFRESHED,
    BUSY,
    UNREADABLE
  }

  @Schema(
      name = "FinanceCollectionChequeHandoverProblem",
      requiredProperties = {"code", "detail"})
  public record SaveProblem(
      String code,
      String detail,
      @Schema(description = "Client row key when the refusal concerns one cheque") String rowKey,
      @Schema(description = "Zero-based index of the refused cheque row") Integer row,
      @Schema(description = "Changed targets when the code is CHEQUE_HANDOVER_STALE")
          List<UUID> obligationIds) {}

  @Schema(name = "FinanceCollectionChequeHandoverRowIntent", enumAsRef = true)
  public enum RowIntent {
    AUTO,
    FIXED,
    NONE
  }

  @Schema(name = "FinanceCollectionChequeHandoverApplicationInput")
  public record ApplicationInput(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID obligationId,
      @NotNull
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(type = "string", example = "1000.00", requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal amount) {}

  @Schema(name = "FinanceCollectionChequeHandoverPreviewRequest")
  public record PreviewRequest(
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID partnerId,
      @Schema(nullable = true) UUID targetObligationId,
      @Schema(defaultValue = "false") boolean includeOtherObligations,
      @NotNull
          @Size(min = 1, max = MAX_ROWS)
          @ArraySchema(
              minItems = 1,
              maxItems = MAX_ROWS,
              schema = @Schema(implementation = PreviewRowInput.class))
          List<@NotNull @Valid PreviewRowInput> rows) {}

  @Schema(name = "FinanceCollectionChequeHandoverPreviewRowInput")
  public record PreviewRowInput(
      @NotBlank
          @Size(max = 100)
          @Schema(maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
          String rowKey,
      @Size(max = 100) @Schema(nullable = true, maxLength = 100) String instrumentReference,
      @Pattern(regexp = "[A-Z]{3}") @Schema(nullable = true, example = "GBP") String currency,
      @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(nullable = true, type = "string", example = "1000.00")
          BigDecimal amount,
      @Schema(nullable = true) LocalDate dueOn,
      @Schema(nullable = true) DrawerKind drawerKind,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RowIntent intent,
      @Size(max = MAX_APPLICATIONS)
          @ArraySchema(
              maxItems = MAX_APPLICATIONS,
              schema = @Schema(implementation = ApplicationInput.class))
          List<@NotNull @Valid ApplicationInput> applications) {
    public PreviewRowInput {
      if (applications == null) applications = List.of();
    }
  }

  @Schema(
      name = "FinanceCollectionChequeHandoverPreviewDto",
      requiredProperties = {"candidates", "rows", "totals", "comparisons"})
  public record Preview(
      List<Candidates> candidates,
      List<PreviewRow> rows,
      List<CurrencyTotal> totals,
      List<MaturityComparison> comparisons,
      @Schema(nullable = true) PreviewNotice notice) {}

  @Schema(name = "FinanceCollectionChequeHandoverPreviewNotice", enumAsRef = true)
  public enum PreviewNotice {
    CHEQUE_HANDOVER_APPLICATION_LIMIT
  }

  @Schema(
      name = "FinanceCollectionChequeHandoverCandidatesDto",
      requiredProperties = {"currency", "obligations", "truncated"})
  public record Candidates(String currency, List<Target> obligations, boolean truncated) {}

  @Schema(
      name = "FinanceCollectionChequeHandoverTargetDto",
      requiredProperties = {"obligationId", "version", "lackingAmount"})
  public record Target(
      UUID obligationId,
      long version,
      @Schema(nullable = true) String orderNumber,
      @Schema(nullable = true) LocalDate expectedDate,
      @Schema(nullable = true) LocalDate plannedChequePaymentDate,
      String lackingAmount) {}

  @Schema(
      name = "FinanceCollectionChequeHandoverPreviewApplicationDto",
      requiredProperties = {"obligationId", "amount"})
  public record PreviewApplication(
      UUID obligationId, String amount, @Schema(nullable = true) Target target) {}

  @Schema(
      name = "FinanceCollectionChequeHandoverPreviewRowDto",
      requiredProperties = {"rowKey", "incomplete", "applications", "issues", "warnings"})
  public record PreviewRow(
      String rowKey,
      boolean incomplete,
      List<PreviewApplication> applications,
      @Schema(nullable = true) String unallocatedAmount,
      List<RowIssue> issues,
      List<RowWarning> warnings) {}

  @Schema(
      name = "FinanceCollectionChequeHandoverRowIssueDto",
      requiredProperties = {"code"})
  public record RowIssue(IssueCode code, @Schema(nullable = true) UUID obligationId) {}

  @Schema(name = "FinanceCollectionChequeHandoverIssueCode", enumAsRef = true)
  public enum IssueCode {
    CHEQUE_AMOUNT_INVALID,
    CHEQUE_APPLICATION_AMOUNT_INVALID,
    CHEQUE_APPLICATION_INCOMPATIBLE,
    CHEQUE_APPLICATION_REQUIRED,
    CHEQUE_APPLICATION_DUPLICATE,
    CHEQUE_DRAWER_KIND_REQUIRED,
    CHEQUE_OVERAPPLICATION,
    CHEQUE_REFERENCE_REQUIRED
  }

  @Schema(
      name = "FinanceCollectionChequeHandoverRowWarningDto",
      requiredProperties = {"code"})
  public record RowWarning(
      WarningCode code,
      @Schema(nullable = true) UUID obligationId,
      @Schema(nullable = true) LocalDate plannedChequePaymentDate) {}

  @Schema(name = "FinanceCollectionChequeHandoverWarningCode", enumAsRef = true)
  public enum WarningCode {
    POSSIBLE_DUPLICATE_CHEQUE_REFERENCE,
    CHEQUE_DUE_AFTER_AGREED_DATE
  }

  @Schema(
      name = "FinanceCollectionChequeHandoverCurrencyTotalDto",
      requiredProperties = {
        "currency",
        "chequeCount",
        "total",
        "ownTotal",
        "endorsedTotal",
        "unallocatedAmount",
        "averageDueOn"
      })
  public record CurrencyTotal(
      String currency,
      int chequeCount,
      String total,
      String ownTotal,
      String endorsedTotal,
      String unallocatedAmount,
      LocalDate averageDueOn) {}

  @Schema(
      name = "FinanceCollectionChequeHandoverMaturityComparisonDto",
      requiredProperties = {
        "orderId",
        "currency",
        "partial",
        "agreedAmount",
        "previouslyCoveredAmount",
        "coveredByHandoverAmount",
        "undatedAmount"
      })
  public record MaturityComparison(
      UUID orderId,
      @Schema(nullable = true) String orderNumber,
      String currency,
      boolean partial,
      String agreedAmount,
      String previouslyCoveredAmount,
      String coveredByHandoverAmount,
      String undatedAmount,
      @Schema(nullable = true) LocalDate agreedAverageDueOn,
      @Schema(nullable = true) LocalDate chequeAverageDueOn,
      @Schema(nullable = true) Long differenceInDays) {}

  @Schema(
      name = "FinanceCollectionChequeHandoverSummaryDto",
      requiredProperties = {"id", "handoverNumber", "partnerId", "receivedOn", "occurredAt"})
  public record HandoverSummary(
      UUID id, String handoverNumber, UUID partnerId, LocalDate receivedOn, Instant occurredAt) {}

  @Schema(
      name = "FinanceCollectionChequeHandoverDetailDto",
      requiredProperties = {
        "id",
        "handoverNumber",
        "partnerId",
        "receivedOn",
        "occurredAt",
        "actorId",
        "reason",
        "evidence",
        "partial",
        "cheques",
        "receiptTotals"
      })
  public record HandoverDetail(
      UUID id,
      String handoverNumber,
      UUID partnerId,
      LocalDate receivedOn,
      Instant occurredAt,
      UUID actorId,
      String reason,
      String evidence,
      @Schema(description = "At least one cheque is outside the viewer's read scope")
          boolean partial,
      List<HandoverCheque> cheques,
      List<ReceiptTotal> receiptTotals) {}

  @Schema(
      name = "FinanceCollectionChequeHandoverChequeDto",
      requiredProperties = {
        "id",
        "ordinal",
        "instrumentReference",
        "currency",
        "amount",
        "dueOn",
        "state",
        "corrected",
        "appliedAmount",
        "unappliedAmount"
      })
  public record HandoverCheque(
      UUID id,
      int ordinal,
      String instrumentReference,
      String currency,
      String amount,
      LocalDate dueOn,
      @Schema(nullable = true) DrawerKind drawerKind,
      @Schema(nullable = true) String drawerName,
      ChequeState state,
      boolean corrected,
      String appliedAmount,
      String unappliedAmount) {}

  @Schema(
      name = "FinanceCollectionChequeHandoverReceiptTotalDto",
      description = "Original RECEIVE evidence of the visible cheques in one currency",
      requiredProperties = {
        "currency",
        "chequeCount",
        "totalRecordedAtReceipt",
        "averageDueOnAtReceipt"
      })
  public record ReceiptTotal(
      String currency,
      int chequeCount,
      String totalRecordedAtReceipt,
      LocalDate averageDueOnAtReceipt) {}
}
