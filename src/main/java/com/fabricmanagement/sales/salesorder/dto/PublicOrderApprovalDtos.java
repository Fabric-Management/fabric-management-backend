package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** The customer's approval page behind an e-mailed link. */
public final class PublicOrderApprovalDtos {

  private PublicOrderApprovalDtos() {}

  /** Where the link stands, as the customer sees it. */
  @Schema(name = "OrderApprovalLinkState", enumAsRef = true)
  public enum LinkState {
    /** Waiting for the customer's decision. */
    OPEN,
    /** The link's time ran out before a decision. */
    EXPIRED,
    APPROVED,
    /** Approved, but the seller has to re-check the terms before the order is processed. */
    APPROVED_NOT_FULFILLABLE,
    CHANGES_REQUESTED,
    /** Taken back by the seller; a newer version may follow. */
    CLOSED
  }

  /** What the page shows before the code is entered: no order content. */
  @Schema(name = "OrderApprovalLink")
  public record LinkView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LinkState state,
      String sellerName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderNumber,
      String customerName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String recipientEmailMasked,
      Instant linkExpiresAt,
      Instant codeSentAt,
      Instant nextCodeAllowedAt,
      Instant decidedAt) {}

  @Schema(name = "OrderApprovalVerifyCode")
  public record VerifyCode(@NotBlank @Pattern(regexp = "\\d{6}") String code) {}

  @Schema(name = "OrderApprovalSession")
  public record Session(@NotBlank @Size(max = 128) String session) {}

  @Schema(name = "OrderApprovalChangeRequest")
  public record RequestChanges(
      @NotBlank @Size(max = 128) String session,
      @NotBlank @Size(max = CustomerApproval.MAX_NOTE_LENGTH) String note) {}

  /** The sent version, once the code was verified. */
  @Schema(name = "OrderApprovalVersion")
  public record VersionView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int versionNo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderVersionContent content,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LinkView link) {}

  /** A verified session: the secret to present with a decision, and the version it opens. */
  @Schema(name = "OrderApprovalVerified")
  public record Verified(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String session,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant sessionExpiresAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) VersionView version) {}
}
