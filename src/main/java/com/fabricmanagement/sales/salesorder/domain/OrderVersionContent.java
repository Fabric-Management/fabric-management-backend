package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the customer was shown, frozen when the version was sent: the order as sales and planning
 * left it, in the words the customer reads. It is never rebuilt from the live order; a page or an
 * approval of the version always reads this.
 */
@Schema(name = "OrderVersionContent")
public record OrderVersionContent(
    String sellerName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderNumber,
    String customerName,
    String customerReference,
    LocalDate orderDate,
    LocalDate requestedDeliveryDate,
    String paymentTerms,
    Terms delivery,
    Contact contact,
    String partialDelivery,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Line> lines,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Total> totals,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unpricedLineCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Request> customRequests,
    Proposal proposal) {

  public OrderVersionContent {
    lines = lines == null ? List.of() : List.copyOf(lines);
    totals = totals == null ? List.of() : List.copyOf(totals);
    customRequests = customRequests == null ? List.of() : List.copyOf(customRequests);
  }

  /** The delivery term the dates refer to, and whether it was proposed or already agreed. */
  @Schema(name = "OrderVersionTerms")
  public record Terms(
      DeliveryTerm term,
      String place,
      IncotermsVersion version,
      DeliveryEvent event,
      DeliveryTermStatus status,
      String contractReference) {}

  /** The customer's contact for the order: who the approval is asked of. */
  @Schema(name = "OrderVersionContact")
  public record Contact(String name, String email, String phone) {}

  /** One product line with its colour, width, quantity, tolerance and price. */
  @Schema(name = "OrderVersionLine")
  public record Line(
      UUID lineId,
      String product,
      String colour,
      BigDecimal finishedWidth,
      String finishedWidthUnit,
      BigDecimal quantity,
      String unit,
      BigDecimal toleranceUpPct,
      BigDecimal toleranceDownPct,
      BigDecimal unitPrice,
      String currency,
      BigDecimal discount,
      BigDecimal tax,
      LocalDate requestedDeliveryDate) {}

  /** What the order amounts to in one agreed currency. */
  @Schema(name = "OrderVersionTotal")
  public record Total(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal subtotal,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal discount,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal tax,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal total) {}

  /** A custom product the customer asked for that is not yet an order line. */
  @Schema(name = "OrderVersionRequest")
  public record Request(
      UUID requestId,
      String description,
      BigDecimal quantity,
      String unit,
      String colourNote,
      BigDecimal width,
      String widthUnit,
      LocalDate requestedDeliveryDate) {}

  /** Planning's date for the delivery term's event, valid until a set time. */
  @Schema(name = "OrderVersionProposal")
  public record Proposal(
      UUID proposalId, LocalDate proposedOn, DeliveryEvent event, Instant validUntil) {}
}
