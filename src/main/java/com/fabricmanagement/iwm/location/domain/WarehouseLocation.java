package com.fabricmanagement.iwm.location.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.common.exception.ProductionDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "warehouse_location", schema = "iwm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WarehouseLocation extends BaseEntity {

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(name = "code", nullable = false, length = 100)
  private String code;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private WarehouseLocationType type;

  @Enumerated(EnumType.STRING)
  @Column(name = "warehouse_type")
  private WarehouseType warehouseType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private LocationStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "storage_condition")
  private StorageCondition storageCondition;

  @Column(name = "path", length = 1000)
  private String path;

  @Column(name = "level")
  private Integer level;

  @Column(name = "sort_order")
  private Integer sortOrder;

  @Column(name = "barcode", length = 100)
  private String barcode;

  @Column(name = "address_id")
  private UUID addressId;

  @Column(name = "max_weight_kg", precision = 12, scale = 3)
  private BigDecimal maxWeightKg;

  @Column(name = "current_weight_kg", precision = 12, scale = 3)
  private BigDecimal currentWeightKg;

  @Column(name = "max_volume_m3", precision = 12, scale = 3)
  private BigDecimal maxVolumeM3;

  @Column(name = "current_volume_m3", precision = 12, scale = 3)
  private BigDecimal currentVolumeM3;

  @Column(name = "linked_machine_id")
  private UUID linkedMachineId;

  public WarehouseLocation(
      UUID parentId,
      String code,
      String name,
      String description,
      WarehouseLocationType type,
      StorageCondition storageCondition,
      String barcode,
      UUID addressId,
      BigDecimal maxWeightKg,
      BigDecimal maxVolumeM3,
      Integer sortOrder,
      UUID linkedMachineId) {
    this.parentId = parentId;
    this.code = code;
    this.name = name;
    this.description = description;
    this.type = type;
    this.status = LocationStatus.AVAILABLE;
    this.storageCondition = storageCondition != null ? storageCondition : StorageCondition.STANDARD;
    this.barcode = barcode;
    this.addressId = addressId;
    this.maxWeightKg = maxWeightKg;
    this.maxVolumeM3 = maxVolumeM3;
    this.currentWeightKg = BigDecimal.ZERO;
    this.currentVolumeM3 = BigDecimal.ZERO;
    this.sortOrder = sortOrder != null ? sortOrder : 0;
    this.linkedMachineId = linkedMachineId;
    this.setIsActive(true);
  }

  public void update(
      String name,
      String description,
      StorageCondition storageCondition,
      String barcode,
      UUID addressId,
      BigDecimal maxWeightKg,
      BigDecimal maxVolumeM3,
      Integer sortOrder,
      UUID linkedMachineId) {
    this.name = name;
    this.description = description;
    this.storageCondition = storageCondition;
    this.barcode = barcode;
    this.addressId = addressId;
    this.maxWeightKg = maxWeightKg;
    this.maxVolumeM3 = maxVolumeM3;
    this.sortOrder = sortOrder;
    this.linkedMachineId = linkedMachineId;
  }

  public void assignPath(String path, int level) {
    this.path = path;
    this.level = level;
  }

  public void changeStatus(LocationStatus newStatus) {
    if (this.status == newStatus) {
      return;
    }
    this.status = newStatus;
  }

  public void addWeight(BigDecimal weight) {
    BigDecimal current = this.currentWeightKg != null ? this.currentWeightKg : BigDecimal.ZERO;
    BigDecimal newWeight = current.add(weight);
    if (this.maxWeightKg != null && newWeight.compareTo(this.maxWeightKg) > 0) {
      throw new ProductionDomainException(
          String.format(
              "Weight %.3f kg would exceed max capacity %.3f kg for location '%s'",
              newWeight, this.maxWeightKg, this.code));
    }
    this.currentWeightKg = newWeight;
    recalculateStatus();
  }

  public void removeWeight(BigDecimal weight) {
    this.currentWeightKg =
        (this.currentWeightKg != null ? this.currentWeightKg : BigDecimal.ZERO).subtract(weight);
    if (this.currentWeightKg.compareTo(BigDecimal.ZERO) < 0) {
      this.currentWeightKg = BigDecimal.ZERO;
    }
    recalculateStatus();
  }

  public void addVolume(BigDecimal volume) {
    BigDecimal current = this.currentVolumeM3 != null ? this.currentVolumeM3 : BigDecimal.ZERO;
    BigDecimal newVolume = current.add(volume);
    if (this.maxVolumeM3 != null && newVolume.compareTo(this.maxVolumeM3) > 0) {
      throw new ProductionDomainException(
          String.format(
              "Volume %.3f m³ would exceed max capacity %.3f m³ for location '%s'",
              newVolume, this.maxVolumeM3, this.code));
    }
    this.currentVolumeM3 = newVolume;
    recalculateStatus();
  }

  public void removeVolume(BigDecimal volume) {
    this.currentVolumeM3 =
        (this.currentVolumeM3 != null ? this.currentVolumeM3 : BigDecimal.ZERO).subtract(volume);
    if (this.currentVolumeM3.compareTo(BigDecimal.ZERO) < 0) {
      this.currentVolumeM3 = BigDecimal.ZERO;
    }
    recalculateStatus();
  }

  public boolean hasCapacity(BigDecimal weight, BigDecimal volume) {
    if (this.status == LocationStatus.BLOCKED || this.status == LocationStatus.MAINTENANCE) {
      return false;
    }
    if (weight != null && this.maxWeightKg != null) {
      BigDecimal currentW = this.currentWeightKg != null ? this.currentWeightKg : BigDecimal.ZERO;
      if (currentW.add(weight).compareTo(this.maxWeightKg) > 0) {
        return false;
      }
    }
    if (volume != null && this.maxVolumeM3 != null) {
      BigDecimal currentV = this.currentVolumeM3 != null ? this.currentVolumeM3 : BigDecimal.ZERO;
      if (currentV.add(volume).compareTo(this.maxVolumeM3) > 0) {
        return false;
      }
    }
    return true;
  }

  public double getUtilizationPercent() {
    BigDecimal weightPct = BigDecimal.ZERO;
    BigDecimal volumePct = BigDecimal.ZERO;
    boolean hasWeightCap = maxWeightKg != null && maxWeightKg.compareTo(BigDecimal.ZERO) > 0;
    boolean hasVolumeCap = maxVolumeM3 != null && maxVolumeM3.compareTo(BigDecimal.ZERO) > 0;
    BigDecimal hundred = BigDecimal.valueOf(100);

    if (hasWeightCap) {
      BigDecimal cw = currentWeightKg != null ? currentWeightKg : BigDecimal.ZERO;
      weightPct = cw.multiply(hundred).divide(maxWeightKg, 2, java.math.RoundingMode.HALF_UP);
    }
    if (hasVolumeCap) {
      BigDecimal cv = currentVolumeM3 != null ? currentVolumeM3 : BigDecimal.ZERO;
      volumePct = cv.multiply(hundred).divide(maxVolumeM3, 2, java.math.RoundingMode.HALF_UP);
    }

    if (hasWeightCap && hasVolumeCap) {
      return weightPct.max(volumePct).doubleValue();
    }
    if (hasWeightCap) return weightPct.doubleValue();
    if (hasVolumeCap) return volumePct.doubleValue();
    return 0;
  }

  private boolean isAtOrOverCapacity() {
    boolean hasWeightCap = maxWeightKg != null && maxWeightKg.compareTo(BigDecimal.ZERO) > 0;
    boolean hasVolumeCap = maxVolumeM3 != null && maxVolumeM3.compareTo(BigDecimal.ZERO) > 0;

    if (hasWeightCap) {
      BigDecimal cw = currentWeightKg != null ? currentWeightKg : BigDecimal.ZERO;
      if (cw.compareTo(maxWeightKg) >= 0) return true;
    }
    if (hasVolumeCap) {
      BigDecimal cv = currentVolumeM3 != null ? currentVolumeM3 : BigDecimal.ZERO;
      if (cv.compareTo(maxVolumeM3) >= 0) return true;
    }
    return false;
  }

  public boolean isMachineLocation() {
    return type == WarehouseLocationType.MACHINE || type == WarehouseLocationType.PRODUCTION_LINE;
  }

  public boolean isStorageLocation() {
    return type == WarehouseLocationType.WAREHOUSE
        || type == WarehouseLocationType.ZONE
        || type == WarehouseLocationType.AISLE
        || type == WarehouseLocationType.BIN;
  }

  public boolean isOperational() {
    return this.getIsActive()
        && this.status != LocationStatus.BLOCKED
        && this.status != LocationStatus.MAINTENANCE;
  }

  private void recalculateStatus() {
    if (this.status == LocationStatus.BLOCKED || this.status == LocationStatus.MAINTENANCE) {
      return;
    }
    if (isAtOrOverCapacity()) {
      this.status = LocationStatus.FULL;
    } else if (this.status == LocationStatus.FULL) {
      this.status = LocationStatus.AVAILABLE;
    }
  }

  @Override
  protected String getModuleCode() {
    return "WH-LOC";
  }
}
