package com.fabricmanagement.logistics.shipment.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sevkiyat satırına fiziksel olarak hangi batch'lerin (partilerin) atandığını tutan birleştirme tablosu.
 */
@Entity
@Table(name = "logistics_shipment_line_batch", schema = "logistics")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentLineBatch extends BaseJunctionEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "shipment_line_id", nullable = false)
  private ShipmentLine shipmentLine;

  /** Fiziksel parti referansı (production/execution/batch modülü). */
  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  /** İlgili partiden ne kadar yüklendi. (Ör: Rulo ağırlığı veya Adet). */
  @Column(name = "quantity", precision = 19, scale = 4, nullable = false)
  private BigDecimal quantity;

  /** Yüklendiği anki zaman damgası. */
  @Column(name = "loaded_at")
  private Instant loadedAt;

  public ShipmentLineBatch(ShipmentLine shipmentLine, UUID batchId, BigDecimal quantity) {
    this.shipmentLine = shipmentLine;
    this.batchId = batchId;
    this.quantity = quantity;
    this.loadedAt = Instant.now();
  }
}
