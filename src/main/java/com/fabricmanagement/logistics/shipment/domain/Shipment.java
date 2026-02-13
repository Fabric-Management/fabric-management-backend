package com.fabricmanagement.logistics.shipment.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.platform.tradingpartner.domain.TradingPartner;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Type;

/**
 * Shipment entity with TradingPartner integration.
 *
 * <p>Supports both inbound and outbound shipments. Uses trading_partner_id as the primary
 * origin/destination partner reference (Faz 1.5 pattern).
 *
 * <h2>TradingPartner Integration:</h2>
 *
 * <ul>
 *   <li>For OUTBOUND shipments: partner is the customer/destination
 *   <li>For INBOUND shipments: partner is the supplier/origin
 * </ul>
 */
@Entity
@Table(
    name = "logistics_shipment",
    schema = "logistics",
    indexes = {
      @Index(name = "idx_ship_tenant", columnList = "tenant_id"),
      @Index(name = "idx_ship_trading_partner", columnList = "trading_partner_id"),
      @Index(name = "idx_ship_status", columnList = "status"),
      @Index(name = "idx_ship_tracking", columnList = "tracking_number"),
      @Index(name = "idx_ship_ship_date", columnList = "ship_date")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ship_tenant_shipment_number",
          columnNames = {"tenant_id", "shipment_number"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shipment extends BaseEntity {

  // ═══════════════════════════════════════════════════════════════════════════
  // TradingPartner Reference (Faz 1.5)
  // ═══════════════════════════════════════════════════════════════════════════

  /** FK to TradingPartner - customer for outbound, supplier for inbound. */
  @Column(name = "trading_partner_id", nullable = false)
  private UUID tradingPartnerId;

  /** Lazy-loaded TradingPartner relationship. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trading_partner_id", insertable = false, updatable = false)
  private TradingPartner tradingPartner;

  // ═══════════════════════════════════════════════════════════════════════════
  // Shipment Identification
  // ═══════════════════════════════════════════════════════════════════════════

  /** Unique shipment number per tenant (e.g., SHP-20260202-00001). */
  @Column(name = "shipment_number", nullable = false, length = 50)
  private String shipmentNumber;

  /** Reference to related order. */
  @Column(name = "order_reference", length = 100)
  private String orderReference;

  /** Shipment type. */
  @Enumerated(EnumType.STRING)
  @Column(name = "shipment_type", nullable = false, length = 20)
  @Builder.Default
  private ShipmentType shipmentType = ShipmentType.OUTBOUND;

  /** Shipment status. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  @Builder.Default
  private ShipmentStatus status = ShipmentStatus.PENDING;

  // ═══════════════════════════════════════════════════════════════════════════
  // Carrier & Tracking
  // ═══════════════════════════════════════════════════════════════════════════

  /** Carrier/courier name. */
  @Column(name = "carrier_name", length = 100)
  private String carrierName;

  /** Carrier code (e.g., UPS, FEDEX, DHL). */
  @Column(name = "carrier_code", length = 50)
  private String carrierCode;

  /** Tracking number. */
  @Column(name = "tracking_number", length = 100)
  private String trackingNumber;

  /** Tracking URL. */
  @Column(name = "tracking_url", length = 500)
  private String trackingUrl;

  // ═══════════════════════════════════════════════════════════════════════════
  // Dates
  // ═══════════════════════════════════════════════════════════════════════════

  /** Ship/dispatch date. */
  @Column(name = "ship_date")
  private LocalDate shipDate;

  /** Estimated delivery date. */
  @Column(name = "estimated_delivery_date")
  private LocalDate estimatedDeliveryDate;

  /** Actual delivery date. */
  @Column(name = "actual_delivery_date")
  private LocalDate actualDeliveryDate;

  /** Timestamp when picked up by carrier. */
  @Column(name = "picked_up_at")
  private Instant pickedUpAt;

  /** Timestamp when delivered. */
  @Column(name = "delivered_at")
  private Instant deliveredAt;

  // ═══════════════════════════════════════════════════════════════════════════
  // Addresses
  // ═══════════════════════════════════════════════════════════════════════════

  /** Origin address. */
  @Column(name = "origin_address", length = 500)
  private String originAddress;

  /** Destination address. */
  @Column(name = "destination_address", nullable = false, length = 500)
  private String destinationAddress;

  // ═══════════════════════════════════════════════════════════════════════════
  // Package Info
  // ═══════════════════════════════════════════════════════════════════════════

  /** Total weight. */
  @Column(name = "total_weight", precision = 10, scale = 3)
  private BigDecimal totalWeight;

  /** Weight unit (KG, LB). */
  @Column(name = "weight_unit", length = 10)
  @Builder.Default
  private String weightUnit = "KG";

  /** Number of packages. */
  @Column(name = "package_count")
  private Integer packageCount;

  // ═══════════════════════════════════════════════════════════════════════════
  // Costs
  // ═══════════════════════════════════════════════════════════════════════════

  /** Shipping cost. */
  @Column(name = "shipping_cost", precision = 19, scale = 4)
  private BigDecimal shippingCost;

  /** Currency code (ISO 4217). */
  @Column(name = "currency", length = 3)
  @Builder.Default
  private String currency = "TRY";

  // ═══════════════════════════════════════════════════════════════════════════
  // Delivery Info
  // ═══════════════════════════════════════════════════════════════════════════

  /** Proof of delivery (signature, photo URL). */
  @Column(name = "delivery_proof", length = 500)
  private String deliveryProof;

  /** Recipient name. */
  @Column(name = "recipient_name", length = 100)
  private String recipientName;

  // ═══════════════════════════════════════════════════════════════════════════
  // Metadata
  // ═══════════════════════════════════════════════════════════════════════════

  /** Notes/comments. */
  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  /** Flexible metadata (customs info, special instructions, etc.). */
  @Type(JsonType.class)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  // ═══════════════════════════════════════════════════════════════════════════
  // BaseEntity
  // ═══════════════════════════════════════════════════════════════════════════

  @Override
  protected String getModuleCode() {
    return "SHP";
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Business Methods
  // ═══════════════════════════════════════════════════════════════════════════

  /** Start preparing the shipment (PENDING → PREPARING). */
  public void startPreparing() {
    if (status != ShipmentStatus.PENDING) {
      throw new IllegalStateException("Can only start preparing PENDING shipments");
    }
    this.status = ShipmentStatus.PREPARING;
  }

  /** Mark as ready for pickup (PREPARING → READY). */
  public void markReady() {
    if (status != ShipmentStatus.PREPARING) {
      throw new IllegalStateException("Can only mark PREPARING shipments as READY");
    }
    this.status = ShipmentStatus.READY;
  }

  /** Record pickup by carrier. */
  public void recordPickup(String carrierName, String trackingNumber) {
    if (status != ShipmentStatus.READY) {
      throw new IllegalStateException("Can only record pickup for READY shipments");
    }
    this.status = ShipmentStatus.PICKED_UP;
    this.carrierName = carrierName;
    this.trackingNumber = trackingNumber;
    this.pickedUpAt = Instant.now();
    this.shipDate = LocalDate.now();
  }

  /** Update status to in transit. */
  public void inTransit() {
    if (status != ShipmentStatus.PICKED_UP) {
      throw new IllegalStateException("Can only mark PICKED_UP shipments as IN_TRANSIT");
    }
    this.status = ShipmentStatus.IN_TRANSIT;
  }

  /** Update status to out for delivery. */
  public void outForDelivery() {
    if (status != ShipmentStatus.IN_TRANSIT) {
      throw new IllegalStateException("Can only mark IN_TRANSIT shipments as OUT_FOR_DELIVERY");
    }
    this.status = ShipmentStatus.OUT_FOR_DELIVERY;
  }

  /** Record delivery. */
  public void recordDelivery(String recipientName, String deliveryProof) {
    if (!status.isInTransit() && status != ShipmentStatus.DELIVERY_FAILED) {
      throw new IllegalStateException("Cannot record delivery for shipment in status: " + status);
    }
    this.status = ShipmentStatus.DELIVERED;
    this.recipientName = recipientName;
    this.deliveryProof = deliveryProof;
    this.deliveredAt = Instant.now();
    this.actualDeliveryDate = LocalDate.now();
  }

  /** Record delivery failure. */
  public void recordDeliveryFailure(String reason) {
    if (!status.isInTransit()) {
      throw new IllegalStateException("Can only record delivery failure for in-transit shipments");
    }
    this.status = ShipmentStatus.DELIVERY_FAILED;
    this.notes = (this.notes != null ? this.notes + "\n" : "") + "Delivery failed: " + reason;
  }

  /** Mark as returned. */
  public void markReturned() {
    if (status != ShipmentStatus.DELIVERY_FAILED) {
      throw new IllegalStateException("Can only mark DELIVERY_FAILED shipments as RETURNED");
    }
    this.status = ShipmentStatus.RETURNED;
  }

  /** Cancel the shipment. */
  public void cancel() {
    if (!status.canCancel()) {
      throw new IllegalStateException("Cannot cancel shipment in status: " + status);
    }
    this.status = ShipmentStatus.CANCELLED;
  }

  /** Update tracking info. */
  public void updateTracking(String trackingNumber, String trackingUrl) {
    this.trackingNumber = trackingNumber;
    this.trackingUrl = trackingUrl;
  }

  /** Check if shipment is late. */
  public boolean isLate() {
    if (estimatedDeliveryDate == null || status.isTerminal()) {
      return false;
    }
    return LocalDate.now().isAfter(estimatedDeliveryDate);
  }
}
