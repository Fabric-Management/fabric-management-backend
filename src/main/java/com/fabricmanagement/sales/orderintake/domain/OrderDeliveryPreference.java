package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The customer's answer to "may ready parts ship first?" for one order (SOI A10). Without a row the
 * preference is UNKNOWN and partial shipment is not assumed.
 */
@Entity
@Table(name = "order_delivery_preference", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDeliveryPreference extends BaseEntity {

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "preference", nullable = false, length = 20)
  private PartialDeliveryPreference preference;

  @Column(name = "customer_contact", length = 200)
  private String customerContact;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", length = 20)
  private AcceptanceChannel channel;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "recorded_by", nullable = false)
  private UUID recordedBy;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  public static OrderDeliveryPreference of(UUID salesOrderId) {
    OrderDeliveryPreference value = new OrderDeliveryPreference();
    value.salesOrderId = salesOrderId;
    value.preference = PartialDeliveryPreference.UNKNOWN;
    return value;
  }

  public void record(
      PartialDeliveryPreference preference,
      String customerContact,
      AcceptanceChannel channel,
      Instant decidedAt,
      UUID recordedBy,
      Instant recordedAt) {
    if (preference == null || recordedBy == null || recordedAt == null) {
      throw new IllegalArgumentException("Preference, recorder and time are required");
    }
    boolean fromCustomer = preference != PartialDeliveryPreference.UNKNOWN;
    if (fromCustomer
        && (customerContact == null
            || customerContact.isBlank()
            || channel == null
            || decidedAt == null)) {
      throw new IllegalArgumentException("The customer's answer needs contact, channel and time");
    }
    this.preference = preference;
    this.customerContact = fromCustomer ? customerContact.trim() : null;
    this.channel = fromCustomer ? channel : null;
    this.decidedAt = fromCustomer ? decidedAt : null;
    this.recordedBy = recordedBy;
    this.recordedAt = recordedAt;
  }

  @Override
  protected String getModuleCode() {
    return "ODP";
  }
}
