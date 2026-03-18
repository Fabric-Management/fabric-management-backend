package com.fabricmanagement.sales.sample.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.offline.domain.OfflineMetadata;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "sample_request", schema = "sales")
@Getter
@Setter
@NoArgsConstructor
public class SampleRequest extends BaseEntity {

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "material_id", nullable = false)
  private UUID materialId;

  @Column(name = "requested_qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal requestedQty;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_method", nullable = false, length = 30)
  private DeliveryMethod deliveryMethod;

  @Type(JsonType.class)
  @Column(name = "delivery_address", columnDefinition = "jsonb")
  private String deliveryAddress;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private SampleRequestStatus status = SampleRequestStatus.REQUESTED;

  @Column(name = "sales_order_id")
  private UUID salesOrderId;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @Embedded private OfflineMetadata offlineMetadata;

  public void markAsDeleted() {
    this.isActive = false;
    super.delete();
  }

  @Override
  public String getModuleCode() {
    return "SALES";
  }
}
