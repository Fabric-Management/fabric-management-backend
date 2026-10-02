package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalChannel;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Sending the order to the customer and the customer's approval, as sales sees them. */
public final class CustomerApprovalDtos {

  private CustomerApprovalDtos() {}

  /** What sales may do about the customer's approval; the backend says whether and why not. */
  @Schema(name = "CustomerApprovalAction", enumAsRef = true)
  public enum Action {
    /** Send the draft's details for the customer's information (nothing to approve). */
    SEND_INFORMATION,
    /** Send the evaluated order for the customer's approval. */
    SEND_FOR_APPROVAL,
    /** Send a new approval link for the same version, possibly to a corrected address. */
    RESEND_LINK
  }

  @Schema(name = "CustomerApprovalCapability")
  public record Capability(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Action action,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
      String reason) {

    public static Capability of(Action action, String reason) {
      return new Capability(action, reason == null, reason);
    }
  }

  @Schema(name = "SendForCustomerApproval")
  public record SendForApproval(
      @Schema(description = "How long the link lasts, in hours; 48 when omitted")
          @Min(1)
          @Max(CustomerApproval.MAX_LINK_HOURS)
          Integer linkValidHours) {}

  @Schema(name = "ResendCustomerApproval")
  public record Resend(
      @Size(max = 200) String recipientName, @Email @Size(max = 255) String recipientEmail) {}

  @Schema(name = "CustomerApprovalView")
  public record ApprovalView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int versionNo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CustomerApprovalStatus status,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean linkExpired,
      String recipientName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String recipientEmail,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant requestedAt,
      Instant sentAt,
      Instant linkExpiresAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int linksIssued,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant proposalValidUntil,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean internalApprovalRequired,
      Instant internalDecidedAt,
      String internalRejectionReason,
      CustomerApprovalChannel decisionChannel,
      Instant decidedAt,
      String decidedByName,
      String decidedByEmail,
      String customerNote,
      String decisionDetail,
      String closedReason,
      Instant closedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean openChangeRequest) {

    public static ApprovalView of(CustomerApproval value, Instant now) {
      return new ApprovalView(
          value.getId(),
          value.getVersionNo(),
          value.getStatus(),
          value.getStatus() == CustomerApprovalStatus.SENT && !value.isOpenAt(now),
          value.getRecipientName(),
          value.getRecipientEmail(),
          value.getRequestedAt(),
          value.getSentAt(),
          value.getLinkExpiresAt(),
          value.getLinksIssued(),
          value.getProposalValidUntil(),
          value.getInternalApprovalRequestId() != null,
          value.getInternalDecidedAt(),
          value.getInternalRejectionReason(),
          value.getDecisionChannel(),
          value.getDecidedAt(),
          value.getDecidedByName(),
          value.getDecidedByEmail(),
          value.getCustomerNote(),
          value.getDecisionDetail(),
          value.getClosedReason(),
          value.getClosedAt(),
          value.hasOpenChangeRequest());
    }
  }

  @Schema(name = "OrderVersionView")
  public record VersionView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int versionNo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderVersionKind kind,
      String recipientEmail,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID frozenBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant frozenAt) {

    public static VersionView of(OrderVersion version) {
      return new VersionView(
          version.getId(),
          version.getVersionNo(),
          version.getKind(),
          version.getContent().contact() == null ? null : version.getContent().contact().email(),
          version.getFrozenBy(),
          version.getFrozenAt());
    }
  }

  /**
   * The order's versions sent to the customer, its approval requests (latest first) and actions.
   */
  @Schema(name = "CustomerApprovalState")
  public record State(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderFlowStage stage,
      String contactName,
      String contactEmail,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ApprovalView> approvals,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<VersionView> versions,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Capability> actions) {}

  /** A customer's change request sales has not followed up yet. */
  @Schema(name = "CustomerChangeRequestItem")
  public record ChangeRequestItem(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID approvalId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderNumber,
      String customerName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int versionNo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String note,
      String decidedByName,
      String decidedByEmail,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant decidedAt) {}
}
