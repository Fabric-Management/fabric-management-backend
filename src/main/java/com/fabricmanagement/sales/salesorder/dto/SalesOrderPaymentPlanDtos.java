package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanInput;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.State;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SalesOrderPaymentPlanDtos {
  private SalesOrderPaymentPlanDtos() {}

  @Schema(name = "SaveSalesOrderPaymentPlanRequest")
  public record Save(
      @NotNull UUID operationId,
      @NotNull @Min(0) Long expectedOrderVersion,
      @NotNull @Min(0) Long expectedPlanVersion,
      @NotNull @Valid PaymentPlanInput paymentPlan,
      @NotBlank @Size(max = 1000) String changeReason) {}

  @Schema(name = "SubmitSalesOrderPaymentPlanRequest")
  public record Submit(
      @NotNull @Min(0) Long expectedOrderVersion,
      @NotNull @Min(0) Long expectedPlanVersion,
      @Min(1) @Max(168) Integer linkValidHours,
      Boolean acknowledgeSettledReduction) {}

  @Schema(name = "SalesOrderPaymentPlanCapability")
  public record Capability(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              allowableValues = {"SAVE_REVISION", "SUBMIT_FOR_APPROVAL"})
          String action,
      boolean allowed,
      @Schema(nullable = true) String reason,
      @Schema(nullable = true) String routesTo,
      boolean needsApproval) {}

  @Schema(name = "SalesOrderPaymentPlanRevisionDto")
  public record Revision(
      UUID id,
      int revisionNo,
      State state,
      PaymentPlanContent content,
      long resultOrderVersion,
      @Schema(nullable = true) UUID approvalVersionId,
      @Schema(nullable = true) UUID agreedApprovalId,
      @Schema(nullable = true) Instant agreedAt,
      @Schema(nullable = true) Instant activatedAt,
      @Schema(nullable = true) LocalDate activationDate,
      Instant recordedAt,
      UUID recordedBy,
      @Schema(nullable = true) String changeReason) {}

  @Schema(name = "SalesOrderPaymentPlanDto")
  public record View(
      UUID orderId,
      long orderVersion,
      @Schema(nullable = true) UUID planId,
      long planVersion,
      boolean cancelled,
      @Schema(nullable = true) Revision draft,
      @Schema(nullable = true) Revision agreed,
      boolean stale,
      List<Capability> capabilities) {}

  @Schema(name = "SalesOrderPaymentPlanConflict")
  public record Conflict(
      String code,
      String detail,
      Long currentOrderVersion,
      Long currentPlanVersion,
      List<String> validationIssues) {}

  @Schema(name = "SalesPaymentPlanPreviewDto")
  public record PreviewResult(PaymentPlanContent content) {}

  @Schema(name = "SalesPaymentPlanPreviewRequest")
  public record Preview(
      @NotNull UUID partnerId,
      @NotNull LocalDate orderDate,
      @NotNull @Size(max = 500) List<@NotNull @Valid SalesOrderLineRequest> lines,
      @NotNull @Valid PaymentPlanInput paymentPlan) {}
}
