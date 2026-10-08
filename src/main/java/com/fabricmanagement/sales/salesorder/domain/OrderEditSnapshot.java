package com.fabricmanagement.sales.salesorder.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The immutable edit projection of an order (CEDIT-02 §3.2): every edit key of the header and of
 * each active line, with the line's product and the digest of its delivery allocations. A server
 * edit base stores one; the current order is projected the same way after its lock, so the two
 * compare key by key. It is never a copy of the whole order and never accepted from a client.
 */
public record OrderEditSnapshot(int schema, long orderVersion, Header header, List<Line> lines) {

  public static final int SCHEMA = 1;

  public OrderEditSnapshot {
    lines =
        lines == null
            ? List.of()
            : lines.stream()
                .sorted(Comparator.comparing(line -> line.lineId().toString()))
                .toList();
  }

  public Optional<Line> line(UUID lineId) {
    return lines.stream().filter(line -> line.lineId().equals(lineId)).findFirst();
  }

  /** The same content at another order version (the version is not part of the content). */
  public OrderEditSnapshot atVersion(long version) {
    return new OrderEditSnapshot(schema, version, header, lines);
  }

  /** Header edit keys. */
  public record Header(
      String customerReference,
      LocalDate orderDate,
      RequestedDateValue requestedDate,
      DeliveryTermsValue deliveryTerms,
      String paymentTerms,
      AgreementValue agreementContext,
      ContactValue contact,
      String shippingAddress,
      String billingAddress,
      String shippingMethod,
      String notes,
      LocalDate deadline) {

    public Header {
      requestedDate = requestedDate == null ? RequestedDateValue.UNKNOWN : requestedDate;
      deliveryTerms = deliveryTerms == null ? DeliveryTermsValue.NONE : deliveryTerms;
      agreementContext = agreementContext == null ? AgreementValue.NONE : agreementContext;
      contact = contact == null ? ContactValue.NONE : contact;
    }

    /** The value of a header key. */
    public Object value(OrderEditKey key) {
      return switch (key) {
        case CUSTOMER_REFERENCE -> customerReference;
        case ORDER_DATE -> orderDate;
        case REQUESTED_DELIVERY_DATE -> requestedDate;
        case DELIVERY_TERMS -> deliveryTerms;
        case PAYMENT_TERMS -> paymentTerms;
        case AGREEMENT_CONTEXT -> agreementContext;
        case CONTACT -> contact;
        case SHIPPING_ADDRESS -> shippingAddress;
        case BILLING_ADDRESS -> billingAddress;
        case SHIPPING_METHOD -> shippingMethod;
        case NOTES -> notes;
        case DEADLINE -> deadline;
        default -> throw new IllegalArgumentException(key + " is not a header key");
      };
    }
  }

  /** Edit keys of one active line, with its product and allocation digest. */
  public record Line(
      UUID lineId,
      long lineVersion,
      UUID productId,
      String productDesc,
      UUID colorId,
      WidthValue finishedWidth,
      LocalDate requestedDeliveryDate,
      boolean singleLotRequired,
      LineShipmentPreference shipmentPreference,
      QuantityValue quantity,
      PricingValue pricing,
      ToleranceValue tolerance,
      SpecificationValue specification,
      String allocationDigest) {

    public Line {
      shipmentPreference =
          shipmentPreference == null ? LineShipmentPreference.AS_READY : shipmentPreference;
      finishedWidth = finishedWidth == null ? WidthValue.NONE : finishedWidth;
      pricing = pricing == null ? PricingValue.NONE : pricing;
      tolerance = tolerance == null ? ToleranceValue.NONE : tolerance;
      specification = specification == null ? SpecificationValue.NONE : specification;
    }

    /** The value of a line key. */
    public Object value(OrderEditKey key) {
      return switch (key) {
        case LINE_PRODUCT_DESC -> productDesc;
        case LINE_COLOR -> colorId;
        case LINE_FINISHED_WIDTH -> finishedWidth;
        case LINE_REQUESTED_DELIVERY_DATE -> requestedDeliveryDate;
        case LINE_SINGLE_LOT_REQUIRED -> singleLotRequired;
        case LINE_SHIPMENT_PREFERENCE -> shipmentPreference;
        case LINE_QUANTITY -> quantity;
        case LINE_PRICING -> pricing;
        case LINE_TOLERANCE -> tolerance;
        case LINE_SPECIFICATION -> specification;
        default -> throw new IllegalArgumentException(key + " is not a line key");
      };
    }
  }

  /** The order-level requested date with what the customer meant (ADR-0014 D7). */
  public record RequestedDateValue(
      RequestedDateStatus status, LocalDate date, RequestedDeliveryEvent event, String place) {
    public static final RequestedDateValue UNKNOWN = new RequestedDateValue(null, null, null, null);

    public static RequestedDateValue of(RequestedDate value) {
      return value == null
          ? UNKNOWN
          : new RequestedDateValue(value.status(), value.date(), value.event(), value.place());
    }

    /**
     * The order form's date applied with the legacy rule ({@link RequestedDate#withLegacyDate}).
     */
    public RequestedDateValue withLegacyDate(LocalDate legacyDate) {
      return of(new RequestedDate(status, date, event, place).withLegacyDate(legacyDate));
    }
  }

  /** Rule, named place, edition and standing of the delivery term; all null without a term. */
  public record DeliveryTermsValue(
      DeliveryTerm term,
      String place,
      IncotermsVersion incotermsVersion,
      DeliveryTermStatus status,
      String contractReference) {
    public static final DeliveryTermsValue NONE =
        new DeliveryTermsValue(null, null, null, null, null);
  }

  public record AgreementValue(AgreementContext context, String note) {
    public static final AgreementValue NONE = new AgreementValue(null, null);
  }

  public record ContactValue(String name, String email, String phone, boolean whatsapp) {
    public static final ContactValue NONE = new ContactValue(null, null, null, false);
  }

  public record WidthValue(BigDecimal value, String unit) {
    public static final WidthValue NONE = new WidthValue(null, null);
  }

  public record QuantityValue(BigDecimal requestedQty, String unit) {}

  public record PricingValue(
      String currency, BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal taxAmount) {
    public static final PricingValue NONE = new PricingValue(null, null, null, null);
  }

  public record ToleranceValue(BigDecimal upPct, BigDecimal downPct) {
    public static final ToleranceValue NONE = new ToleranceValue(null, null);
  }

  /**
   * Module type, module specs and requirement profile: one key, because the profile is resolved
   * from all three together. Equality uses the profile's semantic fingerprint, never its identity
   * or version.
   */
  public record SpecificationValue(
      ModuleType moduleType, Map<String, Object> moduleSpecs, ProfileRef requirementProfile) {
    public static final SpecificationValue NONE = new SpecificationValue(null, null, null);

    public String profileFingerprint() {
      return requirementProfile == null ? null : requirementProfile.fingerprint();
    }
  }

  /** A pinned requirement-profile version: identity, version and semantic fingerprint. */
  public record ProfileRef(UUID profileId, Integer profileVersion, String fingerprint) {}
}
