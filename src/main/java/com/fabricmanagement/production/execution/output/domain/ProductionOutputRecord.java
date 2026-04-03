package com.fabricmanagement.production.execution.output.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.common.exception.ProductionDomainException;
import com.fabricmanagement.production.execution.output.domain.event.ProductionOutputConfirmedEvent;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "production_output_record", schema = "production")
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionOutputRecord extends BaseEntity {

  @Column(name = "work_order_id")
  private UUID workOrderId;

  @Column(name = "work_order_number", length = 30)
  private String workOrderNumber;

  @Column(name = "batch_id")
  private UUID batchId;

  @Column(name = "output_material_id", nullable = false)
  private UUID outputMaterialId;

  @Enumerated(EnumType.STRING)
  @Column(name = "output_material_type", nullable = false, length = 30)
  private MaterialType outputMaterialType;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ProductionOutputStatus status = ProductionOutputStatus.DRAFT;

  @Column(name = "total_item_count", nullable = false)
  private int totalItemCount = 0;

  @Column(name = "total_net_weight", nullable = false, precision = 15, scale = 3)
  private BigDecimal totalNetWeight = BigDecimal.ZERO;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "confirmed_by_user_id")
  private UUID confirmedByUserId;

  @Column(name = "notes", columnDefinition = "text")
  private String notes;

  @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ProductionOutputItem> items = new ArrayList<>();

  @Override
  protected String getModuleCode() {
    return "POUT";
  }

  public static ProductionOutputRecord create(
      UUID tenantId,
      UUID workOrderId,
      String workOrderNumber,
      UUID batchId,
      UUID outputMaterialId,
      MaterialType outputMaterialType,
      String unit,
      String notes) {
    ProductionOutputRecord record = new ProductionOutputRecord();
    record.setTenantId(tenantId);
    record.setWorkOrderId(workOrderId);
    record.setWorkOrderNumber(workOrderNumber);
    record.setBatchId(batchId);
    record.setOutputMaterialId(outputMaterialId);
    record.setOutputMaterialType(outputMaterialType);
    record.setUnit(unit);
    record.setStatus(ProductionOutputStatus.DRAFT);
    record.setTotalItemCount(0);
    record.setTotalNetWeight(BigDecimal.ZERO);
    record.setNotes(notes);
    record.onCreate();
    return record;
  }

  public void addItem(ProductionOutputItem item) {
    if (this.status != ProductionOutputStatus.DRAFT) {
      throw new ProductionDomainException("Items can only be added in DRAFT status");
    }
    if (item.getGrossWeight() != null && item.getGrossWeight().compareTo(item.getNetWeight()) < 0) {
      throw new ProductionDomainException("Gross weight cannot be less than net weight");
    }

    this.items.add(item);
    item.setRecord(this);
    this.totalItemCount++;
    this.totalNetWeight = this.totalNetWeight.add(item.getNetWeight());
  }

  public void removeItem(UUID itemId) {
    if (this.status != ProductionOutputStatus.DRAFT) {
      throw new ProductionDomainException("Items can only be removed in DRAFT status");
    }

    ProductionOutputItem itemToRemove =
        this.items.stream()
            .filter(item -> item.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() -> new ProductionDomainException("Item not found in this record"));

    this.items.remove(itemToRemove);
    itemToRemove.setRecord(null); // break reference for orphan removal
    this.totalItemCount--;
    this.totalNetWeight = this.totalNetWeight.subtract(itemToRemove.getNetWeight());
  }

  public ProductionOutputConfirmedEvent confirm(UUID userId) {
    if (this.status != ProductionOutputStatus.DRAFT) {
      throw new ProductionDomainException("Only DRAFT records can be confirmed");
    }
    if (this.items.isEmpty()) {
      throw new ProductionDomainException("Cannot confirm an empty record");
    }

    this.status = ProductionOutputStatus.CONFIRMED;
    this.confirmedAt = Instant.now();
    this.confirmedByUserId = userId;

    List<ProductionOutputConfirmedEvent.OutputItemData> eventItems =
        this.items.stream()
            .map(
                item ->
                    new ProductionOutputConfirmedEvent.OutputItemData(
                        item.getId(),
                        item.getBarcode(),
                        item.getPackageType(),
                        item.getNetWeight(),
                        item.getGrossWeight(),
                        item.getLocationId()))
            .toList();

    return new ProductionOutputConfirmedEvent(
        this.getTenantId(),
        this.getId(),
        this.workOrderId,
        this.batchId,
        this.outputMaterialId,
        this.outputMaterialType,
        this.getUnit(),
        this.confirmedByUserId,
        eventItems);
  }
}
