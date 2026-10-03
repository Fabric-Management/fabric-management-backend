package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One entry of the order's commercial delivery plan (ADR-0014 D8): who receives the goods, where,
 * under which term, when the customer wants it and whether it ships only complete. It is what is
 * agreed commercially, not a physical shipment: a planned delivery of 600 m may later leave in two
 * vehicles of 300 m, and those shipments are linked to this plan entry by logistics. The delivery
 * term and the requested date either follow the order-level default or are this delivery's own; the
 * source is kept, so a later change of the default reaches only the deliveries that follow it.
 *
 * <p>The consignee may be a third party the customer nominates (e.g. their producer). Receiving the
 * goods grants no approval or change authority (D4).
 */
@Entity
@Table(name = "order_delivery", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDelivery extends BaseEntity {

  public static final int MAX_TRANSPORT_PREFERENCE = 100;

  /** What a delivery holds, as entered; validated by {@link #create} and {@link #revise}. */
  public record Content(
      PartyReference consignee,
      AddressSnapshot shipTo,
      DefaultSource termSource,
      DeliveryTermSetting term,
      DefaultSource requestedDateSource,
      RequestedDate requestedDate,
      String transportPreference,
      boolean shipComplete) {}

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "sequence_no", nullable = false, updatable = false)
  private int sequenceNo;

  @Enumerated(EnumType.STRING)
  @Column(name = "consignee_mode", length = 20)
  private PartyMode consigneeMode;

  @Column(name = "consignee_partner_id")
  private UUID consigneePartnerId;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "name", column = @Column(name = "consignee_name", length = 200)),
    @AttributeOverride(
        name = "contactName",
        column = @Column(name = "consignee_contact_name", length = 120)),
    @AttributeOverride(name = "email", column = @Column(name = "consignee_email", length = 254)),
    @AttributeOverride(name = "phone", column = @Column(name = "consignee_phone", length = 30))
  })
  private PartySnapshot consigneeParty;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "line1", column = @Column(name = "ship_to_line1", length = 200)),
    @AttributeOverride(name = "line2", column = @Column(name = "ship_to_line2", length = 200)),
    @AttributeOverride(name = "city", column = @Column(name = "ship_to_city", length = 100)),
    @AttributeOverride(name = "region", column = @Column(name = "ship_to_region", length = 100)),
    @AttributeOverride(
        name = "postalCode",
        column = @Column(name = "ship_to_postal_code", length = 20)),
    @AttributeOverride(
        name = "countryCode",
        column = @Column(name = "ship_to_country_code", length = 2)),
    @AttributeOverride(
        name = "sourceAddressId",
        column = @Column(name = "ship_to_source_address_id"))
  })
  private AddressSnapshot shipTo;

  @Enumerated(EnumType.STRING)
  @Column(name = "term_source", nullable = false, length = 20)
  private DefaultSource termSource = DefaultSource.ORDER_DEFAULT;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_term", length = 3)
  private DeliveryTerm deliveryTerm;

  @Column(name = "delivery_place", length = DeliveryTerms.MAX_PLACE_LENGTH)
  private String deliveryPlace;

  @Enumerated(EnumType.STRING)
  @Column(name = "incoterms_version", length = 20)
  private IncotermsVersion incotermsVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_term_status", length = 30)
  private DeliveryTermStatus deliveryTermStatus;

  @Column(name = "delivery_contract_reference", length = 200)
  private String deliveryContractReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "requested_date_source", nullable = false, length = 20)
  private DefaultSource requestedDateSource = DefaultSource.ORDER_DEFAULT;

  @Enumerated(EnumType.STRING)
  @Column(name = "requested_date_status", length = 20)
  private RequestedDateStatus requestedDateStatus;

  @Column(name = "requested_date")
  private LocalDate requestedDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "requested_event", length = 40)
  private RequestedDeliveryEvent requestedEvent;

  /** Where the customer said the requested event happens; apart from the term's named place. */
  @Column(name = "requested_place", length = RequestedDate.MAX_PLACE)
  private String requestedPlace;

  @Column(name = "transport_preference", length = MAX_TRANSPORT_PREFERENCE)
  private String transportPreference;

  /**
   * Ships only when every quantity allocated to it is ready (OD-8); production may be staggered.
   */
  @Column(name = "ship_complete", nullable = false)
  private boolean shipComplete;

  public static OrderDelivery create(UUID salesOrderId, int sequenceNo, Content content) {
    if (salesOrderId == null) {
      throw new IllegalArgumentException("Order is required");
    }
    if (sequenceNo < 1) {
      throw new IllegalArgumentException("Deliveries are numbered from 1");
    }
    OrderDelivery value = new OrderDelivery();
    value.salesOrderId = salesOrderId;
    value.sequenceNo = sequenceNo;
    value.revise(content);
    return value;
  }

  /**
   * Replaces what the delivery holds. The caller has validated the consignee against the order's
   * customer ({@link PartyReference#of}) and checks that a registered consignee exists.
   */
  public void revise(Content content) {
    if (content == null) {
      throw new IllegalArgumentException("Content is required");
    }
    PartyReference consignee =
        content.consignee() == null ? PartyReference.NONE : content.consignee();
    DefaultSource termFrom = sourceOrDefault(content.termSource());
    DeliveryTermSetting term = content.term() == null ? DeliveryTermSetting.NONE : content.term();
    if (termFrom == DefaultSource.ORDER_DEFAULT && term.isSet()) {
      throw new OrderDomainException(
          "This delivery follows the order's delivery term; choose its own term to set one");
    }
    if (termFrom == DefaultSource.OVERRIDE && !term.isSet()) {
      throw new OrderDomainException("Choose this delivery's own delivery term and place");
    }
    DefaultSource dateFrom = sourceOrDefault(content.requestedDateSource());
    RequestedDate date =
        content.requestedDate() == null ? RequestedDate.UNKNOWN : content.requestedDate();
    if (dateFrom == DefaultSource.ORDER_DEFAULT && date.isKnown()) {
      throw new OrderDomainException(
          "This delivery follows the order's requested date; choose its own date to set one");
    }
    if (dateFrom == DefaultSource.OVERRIDE && !date.isKnown()) {
      throw new OrderDomainException("Say whether the customer asked for a date for this delivery");
    }
    this.consigneeMode = consignee.mode();
    this.consigneePartnerId = consignee.partnerId();
    this.consigneeParty = consignee.snapshot();
    this.shipTo = content.shipTo();
    this.termSource = termFrom;
    this.deliveryTerm = term.terms().term();
    this.deliveryPlace = term.terms().place();
    this.incotermsVersion = term.terms().version();
    this.deliveryTermStatus = term.status();
    this.deliveryContractReference = term.contractReference();
    this.requestedDateSource = dateFrom;
    this.requestedDateStatus = date.status();
    this.requestedDate = date.date();
    this.requestedEvent = date.event();
    this.requestedPlace = date.place();
    this.transportPreference =
        Text.limited(
            Text.trimmed(content.transportPreference()),
            MAX_TRANSPORT_PREFERENCE,
            "The transport preference is too long");
    this.shipComplete = content.shipComplete();
  }

  public PartyReference getConsignee() {
    return new PartyReference(consigneeMode, consigneePartnerId, consigneeParty);
  }

  /** This delivery's own term; {@link DeliveryTermSetting#NONE} when it follows the order. */
  public DeliveryTermSetting getOwnTerm() {
    if (termSource != DefaultSource.OVERRIDE) {
      return DeliveryTermSetting.NONE;
    }
    return new DeliveryTermSetting(
        new DeliveryTerms(deliveryTerm, deliveryPlace, incotermsVersion),
        deliveryTermStatus,
        deliveryContractReference);
  }

  /**
   * This delivery's own requested date; {@link RequestedDate#UNKNOWN} when it follows the order.
   */
  public RequestedDate getOwnRequestedDate() {
    if (requestedDateSource != DefaultSource.OVERRIDE) {
      return RequestedDate.UNKNOWN;
    }
    return new RequestedDate(requestedDateStatus, requestedDate, requestedEvent, requestedPlace);
  }

  /** The term that applies to this delivery: its own, or the order's. */
  public DeliveryTermSetting effectiveTerm(SalesOrder order) {
    checkOrder(order);
    return termSource == DefaultSource.OVERRIDE ? getOwnTerm() : order.getDeliveryTermSetting();
  }

  /** The requested date that applies to this delivery: its own, or the order's. */
  public RequestedDate effectiveRequestedDate(SalesOrder order) {
    checkOrder(order);
    return requestedDateSource == DefaultSource.OVERRIDE
        ? getOwnRequestedDate()
        : order.getRequestedDate();
  }

  private void checkOrder(SalesOrder order) {
    if (order == null || !salesOrderId.equals(order.getId())) {
      throw new IllegalArgumentException("A delivery resolves its defaults from its own order");
    }
  }

  private static DefaultSource sourceOrDefault(DefaultSource source) {
    return source == null ? DefaultSource.ORDER_DEFAULT : source;
  }

  @Override
  protected String getModuleCode() {
    return "SOD";
  }
}
