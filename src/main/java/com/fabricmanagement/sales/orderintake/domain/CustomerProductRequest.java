package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A customer's request for a product that is not (yet) a catalogue item (SOI K14, R16–R18, N01). It
 * may start without product, technical data or quantity; quantity and unit are required only when
 * it becomes an order line. It belongs to a customer and is attached to at most one draft order.
 * Taking it off the order lets the ready catalogue lines be confirmed (A10: blocking is per line);
 * the origin order is remembered so its shipment still waits for the request unless the customer
 * allowed partial delivery.
 */
@Entity
@Table(name = "customer_product_request", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerProductRequest extends BaseEntity {

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "sales_order_id")
  private UUID salesOrderId;

  /** The order the request was taken off; its delivery still waits for it unless partial. */
  @Column(name = "origin_order_id")
  private UUID originOrderId;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "reference_product_id")
  private UUID referenceProductId;

  @Column(name = "requested_qty", precision = 15, scale = 3)
  private BigDecimal requestedQty;

  @Column(name = "unit", length = 20)
  private String unit;

  @Column(name = "requested_color_note", length = 500)
  private String requestedColorNote;

  @Column(name = "requested_width", precision = 8, scale = 2)
  private BigDecimal requestedWidth;

  @Column(name = "requested_width_unit", length = 10)
  private String requestedWidthUnit;

  @Column(name = "requested_delivery_date")
  private LocalDate requestedDeliveryDate;

  @Column(name = "sample_received_at")
  private Instant sampleReceivedAt;

  @Column(name = "sample_note", columnDefinition = "TEXT")
  private String sampleNote;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private CustomerRequestStatus status;

  @Column(name = "current_revision_no", nullable = false)
  private int currentRevisionNo;

  @Column(name = "resolved_line_id")
  private UUID resolvedLineId;

  /** Line terms the customer's latest approval covers once the request became a line (A11). */
  @Column(name = "approved_line_terms", length = 64)
  private String approvedLineTerms;

  @Column(name = "recorded_by", nullable = false, updatable = false)
  private UUID recordedBy;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  /** The fields a request carries; nulls mean "not known yet". */
  public record Details(
      String description,
      UUID referenceProductId,
      BigDecimal requestedQty,
      String unit,
      String requestedColorNote,
      BigDecimal requestedWidth,
      String requestedWidthUnit,
      LocalDate requestedDeliveryDate,
      Instant sampleReceivedAt,
      String sampleNote) {}

  public static CustomerProductRequest record(
      UUID customerId,
      UUID salesOrderId,
      Details details,
      boolean hasAttachment,
      UUID recordedBy,
      Instant recordedAt) {
    if (customerId == null || recordedBy == null || recordedAt == null) {
      throw new IllegalArgumentException("Customer, recorder and time are required");
    }
    CustomerProductRequest request = new CustomerProductRequest();
    request.customerId = customerId;
    request.salesOrderId = salesOrderId;
    request.apply(details);
    if (!request.hasSampleOrDescription() && !hasAttachment) {
      throw new IllegalArgumentException(
          "A custom request needs a sample record, a file or a description (SOI R16)");
    }
    request.status = CustomerRequestStatus.OPEN;
    request.currentRevisionNo = 0;
    request.recordedBy = recordedBy;
    request.recordedAt = recordedAt;
    return request;
  }

  public void update(Details details, boolean hasAttachment) {
    requireNotFinished();
    apply(details);
    if (!hasSampleOrDescription() && !hasAttachment) {
      throw new IllegalArgumentException(
          "A custom request needs a sample record, a file or a description (SOI R16)");
    }
    if (status == CustomerRequestStatus.NEEDS_INFO) {
      status = CustomerRequestStatus.OPEN;
    }
  }

  public void evaluated(CustomerRequestEvaluationOutcome outcome) {
    requireNotFinished();
    if (outcome == CustomerRequestEvaluationOutcome.NEEDS_INFO) {
      status = CustomerRequestStatus.NEEDS_INFO;
    } else if (outcome == CustomerRequestEvaluationOutcome.NOT_FEASIBLE) {
      status = CustomerRequestStatus.NOT_FEASIBLE;
    }
  }

  public int nextRevision() {
    requireNotFinished();
    currentRevisionNo++;
    status = CustomerRequestStatus.PROPOSAL_READY;
    return currentRevisionNo;
  }

  public void sent() {
    requireNotFinished();
    status = CustomerRequestStatus.SENT_TO_CUSTOMER;
  }

  public void decided(CustomerRequestDecisionOutcome outcome, String currentLineTerms) {
    if (status == CustomerRequestStatus.CLOSED) {
      throw new IllegalStateException("The request is closed");
    }
    if (status == CustomerRequestStatus.RESOLVED) {
      if (outcome != CustomerRequestDecisionOutcome.APPROVED) {
        throw new IllegalStateException(
            "A resolved request's line is changed or cancelled through the order, not rejected");
      }
      approvedLineTerms = currentLineTerms;
      return;
    }
    status =
        outcome == CustomerRequestDecisionOutcome.APPROVED
            ? CustomerRequestStatus.CUSTOMER_APPROVED
            : CustomerRequestStatus.CUSTOMER_REJECTED;
  }

  public void resolve(UUID lineId) {
    if (status != CustomerRequestStatus.CUSTOMER_APPROVED) {
      throw new IllegalStateException("Only an approved request becomes an order line");
    }
    if (requestedQty == null || unit == null) {
      throw new IllegalStateException("Quantity and unit are required before the line is made");
    }
    this.resolvedLineId = Objects.requireNonNull(lineId);
    // Resolution introduces final colour, width and price. Sample approval cannot approve
    // commercial terms that the customer has not seen; decide(APPROVED) records those separately.
    this.approvedLineTerms = null;
    this.status = CustomerRequestStatus.RESOLVED;
  }

  public void attachTo(UUID orderId) {
    requireNotFinished();
    this.salesOrderId = orderId;
  }

  public void detach() {
    requireNotFinished();
    if (salesOrderId == null) {
      throw new IllegalStateException("The request belongs to no order");
    }
    if (originOrderId == null) {
      this.originOrderId = salesOrderId;
    }
    this.salesOrderId = null;
  }

  public void close() {
    if (status == CustomerRequestStatus.RESOLVED) {
      throw new IllegalStateException("A resolved request lives on as its order line");
    }
    this.status = CustomerRequestStatus.CLOSED;
  }

  public boolean coversLine(String lineTerms) {
    return status == CustomerRequestStatus.RESOLVED
        && approvedLineTerms != null
        && approvedLineTerms.equals(lineTerms);
  }

  public boolean hasQuantity() {
    return requestedQty != null && unit != null;
  }

  /** The request terms a customer approval covers before it becomes a line (A11). */
  public String requestTerms() {
    String canonical =
        String.join(
            "|",
            Objects.toString(referenceProductId, ""),
            requestedQty == null ? "" : requestedQty.stripTrailingZeros().toPlainString(),
            unit == null ? "" : unit,
            Objects.toString(requestedColorNote, ""),
            requestedWidth == null ? "" : requestedWidth.stripTrailingZeros().toPlainString(),
            Objects.toString(requestedWidthUnit, ""));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private void apply(Details details) {
    if ((details.requestedQty() == null) != (blank(details.unit()))) {
      throw new IllegalArgumentException("Quantity and unit are given together or not at all");
    }
    if (details.requestedQty() != null && details.requestedQty().signum() <= 0) {
      throw new IllegalArgumentException("A known quantity must be positive");
    }
    if ((details.requestedWidth() == null) != blank(details.requestedWidthUnit())) {
      throw new IllegalArgumentException("Width and its unit are given together or not at all");
    }
    this.description = trim(details.description());
    this.referenceProductId = details.referenceProductId();
    this.requestedQty = details.requestedQty();
    this.unit = blank(details.unit()) ? null : details.unit().trim().toUpperCase(Locale.ROOT);
    this.requestedColorNote = trim(details.requestedColorNote());
    this.requestedWidth = details.requestedWidth();
    this.requestedWidthUnit =
        blank(details.requestedWidthUnit())
            ? null
            : details.requestedWidthUnit().trim().toUpperCase(Locale.ROOT);
    this.requestedDeliveryDate = details.requestedDeliveryDate();
    this.sampleReceivedAt = details.sampleReceivedAt();
    this.sampleNote = trim(details.sampleNote());
  }

  private boolean hasSampleOrDescription() {
    return description != null || sampleNote != null || sampleReceivedAt != null;
  }

  private void requireNotFinished() {
    if (status != null && status.isFinished()) {
      throw new IllegalStateException("The request is " + status);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String trim(String value) {
    return blank(value) ? null : value.trim();
  }

  @Override
  protected String getModuleCode() {
    return "CPR";
  }
}
