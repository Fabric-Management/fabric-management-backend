package com.fabricmanagement.logistics.shipment.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sevkiyat satırı. Ticari siparişin (SalesOrderLine) sevkiyattaki yansımasıdır. Fiziksel
 * karşılıkları (hangi partiler/batchler yüklendi) {@link ShipmentLineBatch} ile tutulur.
 */
@Entity
@Table(
    name = "logistics_shipment_line",
    schema = "logistics",
    indexes = {
      @Index(name = "idx_shp_line_shp_id", columnList = "shipment_id"),
      @Index(name = "idx_shp_line_sol_id", columnList = "sales_order_line_id")
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentLine extends BaseEntity {

  @Column(name = "shipment_id", nullable = false)
  private UUID shipmentId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "shipment_id", insertable = false, updatable = false)
  private Shipment shipment;

  /** Sipariş satırı sırası (Ör: 1, 2, 3...) */
  @Column(name = "line_number", nullable = false)
  private Integer lineNumber;

  /** Neden sevk ediliyor? İlgili satış siparişi satırı referansı. */
  @Column(name = "sales_order_line_id", nullable = false)
  private UUID salesOrderLineId;

  /** Sipariş edilen ürünün sevk edilen toplam miktarı. */
  @Column(name = "quantity", precision = 19, scale = 4, nullable = false)
  private BigDecimal quantity;

  /** Birim (KG, METRE, ADET, TOP). */
  @Enumerated(EnumType.STRING)
  @Column(name = "unit", nullable = false, length = 20)
  private ShipmentUnit unit;

  /** Satırın durumu. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ShipmentLineStatus status = ShipmentLineStatus.PENDING;

  /** Bu sipariş satırına karşılık fiziksel olarak yüklenen parti(batch) detayları. */
  @OneToMany(mappedBy = "shipmentLine", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ShipmentLineBatch> batches = new ArrayList<>();

  @Builder
  public ShipmentLine(
      UUID shipmentId,
      Integer lineNumber,
      UUID salesOrderLineId,
      BigDecimal quantity,
      ShipmentUnit unit) {
    this.shipmentId = shipmentId;
    this.lineNumber = lineNumber;
    this.salesOrderLineId = salesOrderLineId;
    this.quantity = quantity;
    this.unit = unit;
    this.status = ShipmentLineStatus.PENDING;
    this.batches = new ArrayList<>();
  }

  @Override
  protected String getModuleCode() {
    return "SHL";
  }

  public BigDecimal totalLoadedQuantity() {
    return batches.stream()
        .map(ShipmentLineBatch::getQuantity)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public void addBatch(UUID batchId, BigDecimal batchQuantity, String qualityGradeSnapshot) {
    ShipmentLineBatch lineBatch =
        new ShipmentLineBatch(this, batchId, batchQuantity, qualityGradeSnapshot);
    this.batches.add(lineBatch);
  }

  public void removeBatch(ShipmentLineBatch batch) {
    this.batches.remove(batch);
    batch.setShipmentLine(null);
  }

  public void markAsLoaded() {
    this.status = ShipmentLineStatus.LOADED;
  }

  public void markAsCancelled() {
    this.status = ShipmentLineStatus.CANCELLED;
  }
}
