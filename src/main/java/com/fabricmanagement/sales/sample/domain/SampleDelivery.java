package com.fabricmanagement.sales.sample.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sample_delivery", schema = "sales")
@Getter
@Setter
@NoArgsConstructor
public class SampleDelivery extends BaseEntity {

  @Column(name = "sample_request_id", nullable = false)
  private UUID sampleRequestId;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_method", nullable = false, length = 30)
  private DeliveryMethod deliveryMethod;

  @Column(name = "tracking_number", length = 100)
  private String trackingNumber;

  @Column(name = "cargo_company", length = 100)
  private String cargoCompany;

  @Column(name = "delivered_by_id")
  private UUID deliveredById;

  @Column(name = "dispatched_at")
  private Instant dispatchedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "recipient_name", length = 255)
  private String recipientName;

  @Column(name = "delivery_photo", columnDefinition = "TEXT")
  private String deliveryPhoto;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  public void markAsDeleted() {
    this.isActive = false;
    super.delete();
  }

  @Override
  public String getModuleCode() {
    return "SALES";
  }
}
