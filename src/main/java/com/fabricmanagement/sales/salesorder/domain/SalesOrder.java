package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.offline.domain.OfflineMetadata;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Type;

/**
 * Sales order entity with TradingPartner integration.
 *
 * <p>Uses trading_partner_id as the primary customer reference (Faz 1.5 pattern). This entity does
 * NOT have a legacy company_id column - it's a clean implementation.
 *
 * <h2>TradingPartner Integration:</h2>
 *
 * <ul>
 *   <li>trading_partner_id is NOT NULL - all orders must have a partner
 *   <li>Use TradingPartnerResolver in service layer to resolve partner IDs
 *   <li>For SALES orders: partner is the customer
 *   <li>For PURCHASE orders: partner is the supplier
 * </ul>
 */
@Entity
@Table(
    name = "sales_order",
    schema = "sales_ord",
    indexes = {
      @Index(name = "idx_so_tenant", columnList = "tenant_id"),
      @Index(name = "idx_so_trading_partner", columnList = "trading_partner_id"),
      @Index(name = "idx_so_status", columnList = "status"),
      @Index(name = "idx_so_order_date", columnList = "order_date")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrder extends BaseEntity {

  @Column(name = "creation_seq", insertable = false, updatable = false)
  private Long creationSeq;

  @Enumerated(EnumType.STRING)
  @Column(name = "cover_regime", length = 20)
  @Setter(AccessLevel.NONE)
  private OrderCoverRegime coverRegime;

  public void decideCoverRegime(OrderCoverRegime regime) {
    if (regime == null) throw new IllegalArgumentException("Order-cover regime is required");
    if (coverRegime != null && coverRegime != regime) {
      throw new OrderDomainException("Order-cover regime is immutable", 409);
    }
    coverRegime = regime;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // TradingPartner Reference (Faz 1.5)
  // ═══════════════════════════════════════════════════════════════════════════

  /** FK to TradingPartner - primary partner reference. */
  @Column(name = "trading_partner_id", nullable = false)
  private UUID tradingPartnerId;

  /** Lazy-loaded TradingPartner relationship. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trading_partner_id", insertable = false, updatable = false)
  private TradingPartner tradingPartner;

  // ═══════════════════════════════════════════════════════════════════════════
  // Order Identification
  // ═══════════════════════════════════════════════════════════════════════════

  /** Unique order number per tenant (e.g., SO-20260202-00001). */
  @Column(name = "order_number", nullable = false, length = 50)
  private String orderNumber;

  /** Customer's purchase order reference. */
  @Column(name = "customer_reference", length = 100)
  private String customerReference;

  /** Order type. */
  @Enumerated(EnumType.STRING)
  @Column(name = "order_type", nullable = false, length = 20)
  @Builder.Default
  private OrderType orderType = OrderType.SALES;

  /** Order status. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  @Builder.Default
  private OrderStatus status = OrderStatus.DRAFT;

  /**
   * Where the order stands in sales → planning → customer approval; a separate axis from {@link
   * #status}. Moved only through {@link #moveFlowTo}.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "flow_stage", nullable = false, length = 30)
  @Setter(AccessLevel.NONE)
  @Builder.Default
  private OrderFlowStage flowStage = OrderFlowStage.DRAFT;

  /**
   * Counts the hand-overs to planning. A proposal belongs to the round it was made in; after the
   * order is taken back and handed over again, an earlier proposal is not reused.
   */
  @Column(name = "planning_round", nullable = false)
  @Setter(AccessLevel.NONE)
  @Builder.Default
  private int planningRound = 0;

  /**
   * Counts the evaluations within the order's planning history. Reopening a finished evaluation
   * starts a new one: a proposal made before the reopening no longer completes planning.
   */
  @Column(name = "planning_evaluation", nullable = false)
  @Setter(AccessLevel.NONE)
  @Builder.Default
  private int planningEvaluation = 0;

  /** When the order was last handed to planning. */
  @Column(name = "planning_submitted_at")
  @Setter(AccessLevel.NONE)
  private java.time.Instant planningSubmittedAt;

  /** Status before ON_HOLD, used to restore on resume. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status_before_hold", length = 30)
  private OrderStatus statusBeforeHold;

  /** Reason for rejection. */
  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  // ═══════════════════════════════════════════════════════════════════════════
  // Dates
  // ═══════════════════════════════════════════════════════════════════════════

  /** Order creation date. */
  @Column(name = "order_date", nullable = false)
  private LocalDate orderDate;

  /**
   * The delivery date the customer asked for: their request, kept as told. It never takes the
   * meaning of the delivery term's event; choosing FCA later does not turn "in my warehouse on the
   * 20th" into "handed to the carrier on the 20th".
   */
  @Column(name = "requested_delivery_date")
  private LocalDate requestedDeliveryDate;

  /**
   * The current agreed committed date for the delivery event. Written only by recording a delivery
   * commitment, whose append-only history keeps the first promise and every change.
   */
  @Column(name = "committed_on")
  @Setter(AccessLevel.NONE)
  private LocalDate committedOn;

  // ── Delivery term (Incoterms) ────────────────────────────────────────────

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_term", length = 3)
  @Setter(AccessLevel.NONE)
  private DeliveryTerm deliveryTerm;

  /** The place named with the term; under C-terms this is the destination. */
  @Column(name = "delivery_place", length = DeliveryTerms.MAX_PLACE_LENGTH)
  @Setter(AccessLevel.NONE)
  private String deliveryPlace;

  @Enumerated(EnumType.STRING)
  @Column(name = "incoterms_version", length = 20)
  @Setter(AccessLevel.NONE)
  private IncotermsVersion incotermsVersion;

  /** Proposed or agreed; null while no term is entered. */
  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_term_status", length = 30)
  @Setter(AccessLevel.NONE)
  private DeliveryTermStatus deliveryTermStatus;

  /** The contract that fixed the term, when {@link DeliveryTermStatus#AGREED_BY_CONTRACT}. */
  @Column(name = "delivery_contract_reference", length = 200)
  @Setter(AccessLevel.NONE)
  private String deliveryContractReference;

  /** Actual delivery date. */
  @Column(name = "actual_delivery_date")
  private LocalDate actualDeliveryDate;

  // ═══════════════════════════════════════════════════════════════════════════
  // Commercial terms
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Agreed payment terms. The order carries no currency or amounts of its own: each line keeps its
   * agreed price and currency, and the order totals are derived per currency from the lines (see
   * {@link OrderCurrencyTotals}). The currency a payment is actually made in is recorded at
   * collection, not here.
   */
  @Column(name = "payment_terms", length = 200)
  private String paymentTerms;

  /** Where the conversation that led to the order took place; context, not acceptance. */
  @Enumerated(EnumType.STRING)
  @Column(name = "agreement_context", length = 30)
  @Setter(AccessLevel.NONE)
  private AgreementContext agreementContext;

  /** Description of an {@link AgreementContext#OTHER} context. */
  @Column(name = "agreement_context_note", length = 500)
  @Setter(AccessLevel.NONE)
  private String agreementContextNote;

  /**
   * The customer's contact person for this order, kept as typed or chosen at order entry. It is a
   * snapshot: a person who is not (yet) in the partner's contact list stays on the order, and the
   * partner's contacts are never changed by an order.
   */
  @Column(name = "contact_name", length = 120)
  private String contactName;

  @Column(name = "contact_email", length = 254)
  private String contactEmail;

  @Column(name = "contact_phone", length = 30)
  private String contactPhone;

  /** Whether the contact's phone may be notified on WhatsApp. */
  @Column(name = "contact_whatsapp", nullable = false)
  @Builder.Default
  private boolean contactWhatsapp = false;

  // ═══════════════════════════════════════════════════════════════════════════
  // Shipping
  // ═══════════════════════════════════════════════════════════════════════════

  /** Shipping address. */
  @Column(name = "shipping_address", length = 500)
  private String shippingAddress;

  /** Billing address. */
  @Column(name = "billing_address", length = 500)
  private String billingAddress;

  /** Shipping method. */
  @Column(name = "shipping_method", length = 50)
  private String shippingMethod;

  // ═══════════════════════════════════════════════════════════════════════════
  // Faz 2 — Module & Traceability
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Production module type for this order (FIBER / YARN / FABRIC / DYE_FINISHING). Drives
   * moduleSpecs validation on SalesOrderLine level.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "module_type", length = 20)
  private ModuleType moduleType;

  /** Customer-requested deadline for delivery of all lines. */
  @Column(name = "deadline")
  private LocalDate deadline;

  /** FK → Quote — populated when order was converted from a quote. */
  @Column(name = "quote_id")
  private UUID quoteId;

  /** FK → SampleRequest — populated when order originated from a sample request. */
  @Column(name = "sample_request_id")
  private UUID sampleRequestId;

  // ═══════════════════════════════════════════════════════════════════════════
  // Metadata
  // ═══════════════════════════════════════════════════════════════════════════

  /** Notes/comments. */
  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  /** Flexible metadata (payment terms, incoterms, etc.). */
  @Type(JsonType.class)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  // ═══════════════════════════════════════════════════════════════════════════
  // Offline Sync
  // ═══════════════════════════════════════════════════════════════════════════
  @Embedded private OfflineMetadata offlineMetadata;

  // ═══════════════════════════════════════════════════════════════════════════
  // BaseEntity
  // ═══════════════════════════════════════════════════════════════════════════

  @Override
  protected String getModuleCode() {
    return "SO";
  }

  @Override
  public void delete() {
    if (!status.canDelete()) {
      throw new OrderDomainException(
          "Only DRAFT orders can be deleted. Current status: "
              + status
              + ". Use cancel() for non-draft orders.");
    }
    super.delete();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Business Methods
  // ═══════════════════════════════════════════════════════════════════════════

  /** Revise a rejected order back to DRAFT. */
  public void reviseRejected() {
    if (status != OrderStatus.REJECTED) {
      throw new OrderDomainException(
          String.format(
              "Cannot revise order %s: only REJECTED orders can be revised. Current: %s",
              orderNumber, status),
          409);
    }
    this.status = OrderStatus.DRAFT;
    this.rejectionReason = null;
  }

  /**
   * Update order details. Only allowed in DRAFT status. Rich Domain Model — edit guard lives in the
   * entity.
   *
   * @throws OrderDomainException with HTTP 409 if order is not in DRAFT status
   */
  /** The agreed delivery term, or {@link DeliveryTerms#NONE}. */
  public DeliveryTerms getDeliveryTerms() {
    return deliveryTerm == null
        ? DeliveryTerms.NONE
        : new DeliveryTerms(deliveryTerm, deliveryPlace, incotermsVersion);
  }

  /** The event the order's dates refer to, or null while no term is agreed. */
  public DeliveryEvent getDeliveryEvent() {
    return deliveryTerm == null ? null : deliveryTerm.event();
  }

  /** Sets an already validated term ({@link DeliveryTerms#of}). */
  public void applyDeliveryTerms(DeliveryTerms terms) {
    DeliveryTerms value = terms == null ? DeliveryTerms.NONE : terms;
    this.deliveryTerm = value.term();
    this.deliveryPlace = value.place();
    this.incotermsVersion = value.version();
  }

  /**
   * Sets whether the term is proposed or agreed by contract, as entered. A term without a status is
   * a proposal; agreement by the customer comes only from the customer's approval ({@link
   * #markDeliveryTermAgreedByCustomer()}). Without a term there is no status.
   */
  public void applyDeliveryTermStatus(DeliveryTermStatus status, String contractReference) {
    String reference =
        contractReference == null || contractReference.isBlank() ? null : contractReference.trim();
    if (deliveryTerm == null) {
      if (status != null || reference != null) {
        throw new OrderDomainException("Choose the delivery term before its status");
      }
      this.deliveryTermStatus = null;
      this.deliveryContractReference = null;
      return;
    }
    DeliveryTermStatus value = status == null ? DeliveryTermStatus.PROPOSED : status;
    if (value == DeliveryTermStatus.AGREED_BY_CUSTOMER) {
      throw new OrderDomainException(
          "A term is agreed by the customer only through the customer's approval");
    }
    if (value == DeliveryTermStatus.AGREED_BY_CONTRACT && reference == null) {
      throw new OrderDomainException("Name the contract that fixed the delivery term");
    }
    if (value != DeliveryTermStatus.AGREED_BY_CONTRACT && reference != null) {
      throw new OrderDomainException("A contract reference belongs to a term agreed by contract");
    }
    if (reference != null && reference.length() > 200) {
      throw new OrderDomainException("The contract reference is too long");
    }
    this.deliveryTermStatus = value;
    this.deliveryContractReference = reference;
  }

  /** The customer approved the sent order version, and with it the delivery term. */
  public void markDeliveryTermAgreedByCustomer() {
    if (deliveryTerm == null) {
      throw new OrderDomainException("There is no delivery term to agree");
    }
    if (deliveryTermStatus != DeliveryTermStatus.AGREED_BY_CONTRACT) {
      this.deliveryTermStatus = DeliveryTermStatus.AGREED_BY_CUSTOMER;
    }
  }

  /** Context of the conversation; "other" needs its description, which only "other" carries. */
  public void applyAgreementContext(AgreementContext context, String note) {
    String text = note == null || note.isBlank() ? null : note.trim();
    if (context == AgreementContext.OTHER && text == null) {
      throw new OrderDomainException("Describe where the order was agreed");
    }
    if (context != AgreementContext.OTHER && text != null) {
      throw new OrderDomainException("A description belongs to the \"other\" choice");
    }
    if (text != null && text.length() > 500) {
      throw new OrderDomainException("The description is too long");
    }
    this.agreementContext = context;
    this.agreementContextNote = text;
  }

  /** A new hand-over to planning: earlier proposals no longer count. */
  public void startPlanningRound(java.time.Instant at) {
    this.planningRound = planningRound + 1;
    this.planningSubmittedAt = at;
  }

  /** A reopened evaluation: what planning proposed before has to be proposed or confirmed again. */
  public void startNewEvaluation() {
    this.planningEvaluation = planningEvaluation + 1;
  }

  /**
   * Closed for any further work: delivered, cancelled, rejected or fully shipped. A closed order
   * keeps its flow stage for the record, but no work on it is taken, assigned or done.
   */
  public boolean isClosedForWork() {
    return status.isTerminal() || status == OrderStatus.SHIPPED;
  }

  /** The order's content is with planning or out for an approval; it changes in the draft. */
  public static final String WITH_PLANNING = "ORDER_WITH_PLANNING";

  /** The customer approved the order; its content changes only through a revision. */
  public static final String APPROVED_BY_CUSTOMER = "ORDER_APPROVED_BY_CUSTOMER";

  /**
   * Why what planning evaluated and the customer is asked to approve (products, quantities,
   * tolerances, prices, delivery term, customer requests) cannot change now, or null when it can.
   * While the order is with planning or out for an approval it changes by taking the order back to
   * the draft; once the customer approved it (or it was confirmed) it is fixed.
   */
  public String commercialContentLock() {
    OrderFlowStage stage = flowStage == null ? OrderFlowStage.DRAFT : flowStage;
    if (stage == OrderFlowStage.CUSTOMER_APPROVED || CONFIRMED_OR_LATER.contains(status)) {
      return APPROVED_BY_CUSTOMER;
    }
    return stage.locksCommercialContent() ? WITH_PLANNING : null;
  }

  /** Rejects a change to the order's commercial content (see {@link #commercialContentLock()}). */
  public void assertCommercialContentEditable() {
    String lock = commercialContentLock();
    if (APPROVED_BY_CUSTOMER.equals(lock)) {
      throw OrderDomainException.stage(
          APPROVED_BY_CUSTOMER,
          "Order "
              + orderNumber
              + " was approved by the customer: its content changes only through a revision the"
              + " customer approves");
    }
    if (lock != null) {
      throw OrderDomainException.withPlanning(
          "Order "
              + orderNumber
              + " is "
              + flowStage
              + ": take it back to the draft with a reason to change what was evaluated");
    }
  }

  private static final java.util.Set<OrderStatus> CONFIRMED_OR_LATER =
      java.util.EnumSet.of(
          OrderStatus.CONFIRMED,
          OrderStatus.IN_PROGRESS,
          OrderStatus.PARTIALLY_SHIPPED,
          OrderStatus.SHIPPED,
          OrderStatus.DELIVERED,
          OrderStatus.ON_HOLD);

  /** Statuses in which an approved or confirmed order is being fulfilled. */
  private static final java.util.Set<OrderStatus> PROCESSING =
      java.util.EnumSet.of(
          OrderStatus.CONFIRMED,
          OrderStatus.IN_PROGRESS,
          OrderStatus.PARTIALLY_SHIPPED,
          OrderStatus.ON_HOLD);

  /**
   * Planning inputs (greige cover, production and ship readiness) are taken while planning
   * evaluates the order and while the approved order is being fulfilled. Once planning has finished
   * or the order went to the customer, the evaluation is reopened first, so that what the proposal
   * and the sent version rest on never changes silently.
   */
  public void assertAcceptsPlanningInput() {
    OrderFlowStage stage = flowStage == null ? OrderFlowStage.DRAFT : flowStage;
    if (isClosedForWork()) {
      throw com.fabricmanagement.sales.common.exception.OrderDomainException.stage(
          "ORDER_CLOSED", "Order " + orderNumber + " is " + status);
    }
    if (stage == OrderFlowStage.IN_PLANNING || PROCESSING.contains(status)) {
      return;
    }
    if (stage == OrderFlowStage.PLANNED || stage.awaitsApproval()) {
      throw com.fabricmanagement.sales.common.exception.OrderDomainException.stage(
          "EVALUATION_CLOSED",
          "Planning finished this evaluation; reopen it with a reason before changing its basis");
    }
    throw com.fabricmanagement.sales.common.exception.OrderDomainException.stage(
        "EVALUATION_NOT_STARTED",
        "Order " + orderNumber + " is " + stage + ": planning has not started evaluating it");
  }

  /** The arrival estimate belongs to an order in fulfilment, up to its delivery. */
  public void assertAcceptsArrivalEstimate() {
    if (!PROCESSING.contains(status) && status != OrderStatus.SHIPPED) {
      throw com.fabricmanagement.sales.common.exception.OrderDomainException.stage(
          "ORDER_NOT_IN_PROCESSING",
          "Order " + orderNumber + " is " + status + ": not in fulfilment");
    }
  }

  /** Moves the order to the next flow stage; a move the flow does not allow is a conflict. */
  public OrderFlowStage moveFlowTo(OrderFlowStage next) {
    OrderFlowStage current = flowStage == null ? OrderFlowStage.DRAFT : flowStage;
    if (next == null || !current.canMoveTo(next)) {
      throw new OrderDomainException(
          "Order " + orderNumber + " cannot move from " + current + " to " + next, 409);
    }
    this.flowStage = next;
    return current;
  }

  /** Called only when a delivery commitment is recorded; it is the current agreed date. */
  public void applyCommittedDate(LocalDate committedOn) {
    this.committedOn = committedOn;
  }

  public void updateDraft(SalesOrderUpdateCommand cmd) {
    if (!status.canEdit()) {
      throw new OrderDomainException(
          "Cannot edit order "
              + orderNumber
              + ": current status "
              + status
              + " does not allow editing. Only DRAFT orders can be modified.",
          409);
    }
    this.customerReference = cmd.customerReference();
    this.orderDate = cmd.orderDate();
    this.requestedDeliveryDate = cmd.requestedDeliveryDate();
    applyDeliveryTerms(cmd.deliveryTerms());
    applyDeliveryTermStatus(cmd.deliveryTermStatus(), cmd.deliveryContractReference());
    this.paymentTerms = cmd.paymentTerms();
    applyAgreementContext(cmd.agreementContext(), cmd.agreementContextNote());
    this.contactName = cmd.contactName();
    this.contactEmail = cmd.contactEmail();
    this.contactPhone = cmd.contactPhone();
    this.contactWhatsapp = cmd.contactWhatsapp();
    this.shippingAddress = cmd.shippingAddress();
    this.billingAddress = cmd.billingAddress();
    this.shippingMethod = cmd.shippingMethod();
    this.notes = cmd.notes();
    this.metadata = cmd.metadata();
    this.moduleType = cmd.derivedModuleType();
    this.deadline = cmd.deadline();
  }

  /**
   * The customer approved the sent version (the flow is at {@link
   * OrderFlowStage#CUSTOMER_APPROVED}): the order is confirmed and its delivery term agreed. The
   * caller has re-checked that the approved terms can still be met.
   */
  public void confirmByCustomer() {
    if (status != OrderStatus.DRAFT) {
      throw OrderDomainException.stage(
          "ORDER_NOT_DRAFT", "Order " + orderNumber + " is " + status + ": not awaiting approval");
    }
    if (flowStage != OrderFlowStage.CUSTOMER_APPROVED) {
      throw OrderDomainException.stage(
          "NOT_APPROVED_BY_CUSTOMER", "Order " + orderNumber + " is " + flowStage);
    }
    this.status = OrderStatus.CONFIRMED;
    markDeliveryTermAgreedByCustomer();
  }

  /**
   * Demo data only: confirms a seeded order without the planning and customer approval flow, so a
   * fresh playground has orders in fulfilment. No API reaches it.
   */
  public void confirmSeededDemoOrder() {
    if (status != OrderStatus.DRAFT) {
      throw new OrderDomainException(
          String.format("Order can only be confirmed from DRAFT status. Current: %s", status), 409);
    }
    this.status = OrderStatus.CONFIRMED;
  }

  /** Start processing (CONFIRMED → IN_PRODUCTION). */
  public void startProcessing() {
    if (status != OrderStatus.CONFIRMED) {
      throw new OrderDomainException(
          String.format(
              "Cannot start processing order %s: current status is %s (must be CONFIRMED)",
              orderNumber, status));
    }
    this.status = OrderStatus.IN_PROGRESS;
  }

  /**
   * Marks the order as in progress when production starts.
   *
   * @return true when the status changed, false when this event is duplicate or out of order
   */
  public boolean markInProgressIfConfirmed() {
    if (status != OrderStatus.CONFIRMED) {
      return false;
    }
    this.status = OrderStatus.IN_PROGRESS;
    return true;
  }

  /** Mark as shipped. */
  public void ship() {
    if (!status.canShip()) {
      throw new OrderDomainException(
          String.format(
              "Cannot ship order %s: current status %s does not allow shipping",
              orderNumber, status));
    }
    this.status = OrderStatus.SHIPPED;
  }

  /** Mark as delivered. */
  public void deliver(LocalDate deliveryDate) {
    if (status != OrderStatus.SHIPPED) {
      throw new OrderDomainException(
          String.format(
              "Cannot deliver order %s: current status is %s (must be SHIPPED)",
              orderNumber, status));
    }
    this.status = OrderStatus.DELIVERED;
    this.actualDeliveryDate = deliveryDate;
  }

  /** Cancel the order. */
  public void cancel() {
    if (!status.canCancel()) {
      throw new OrderDomainException(
          String.format(
              "Cannot cancel order %s: current status %s does not allow cancellation",
              orderNumber, status),
          409);
    }
    this.status = OrderStatus.CANCELLED;
  }

  /** Put order on hold. */
  public void hold() {
    if (status.isTerminal() || status == OrderStatus.ON_HOLD) {
      throw new OrderDomainException(
          String.format(
              "Cannot hold order %s: status %s is terminal or already ON_HOLD",
              orderNumber, status),
          409);
    }
    this.statusBeforeHold = this.status;
    this.status = OrderStatus.ON_HOLD;
  }

  /** Resume an order from hold. */
  public void resume() {
    if (status != OrderStatus.ON_HOLD) {
      throw new OrderDomainException(
          String.format(
              "Cannot resume order %s: current status is %s (must be ON_HOLD)",
              orderNumber, status),
          409);
    }
    if (statusBeforeHold == null) {
      throw new IllegalStateException(
          String.format("Cannot resume order %s: statusBeforeHold is null", orderNumber));
    }
    this.status = this.statusBeforeHold;
    this.statusBeforeHold = null;
  }

  /**
   * Sevkiyat ilerlemesine göre header status'ünü günceller. Listener, tüm aktif satırların sevk
   * durumunu aggregate edip bu metodu çağırır.
   *
   * <p>Geçiş kuralları:
   *
   * <ul>
   *   <li>Terminal durumlarda (DELIVERED/CANCELLED/REJECTED) → no-op (sessiz dön)
   *   <li>allLinesFullyShipped && canShip() → SHIPPED
   *   <li>!allLinesFullyShipped && anyLineShipped && canShip() → PARTIALLY_SHIPPED
   *   <li>İkisi de false → değişiklik yok
   * </ul>
   */
  public void recordShipmentProgress(boolean allLinesFullyShipped, boolean anyLineShipped) {
    if (status.isTerminal()) {
      return;
    }

    if (allLinesFullyShipped && status.canShip()) {
      this.status = OrderStatus.SHIPPED;
    } else if (anyLineShipped && status.canShip()) {
      this.status = OrderStatus.PARTIALLY_SHIPPED;
    }
  }
}
