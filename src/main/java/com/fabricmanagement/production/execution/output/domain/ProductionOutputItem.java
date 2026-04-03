package com.fabricmanagement.production.execution.output.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "production_output_item", schema = "production")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionOutputItem extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "record_id", nullable = false)
  private ProductionOutputRecord record;

  @Column(name = "barcode", length = 60)
  private String barcode;

  @Enumerated(EnumType.STRING)
  @Column(name = "package_type", nullable = false, length = 20)
  private PackageType packageType;

  @Column(name = "net_weight", nullable = false, precision = 15, scale = 3)
  private BigDecimal netWeight;

  @Column(name = "gross_weight", precision = 15, scale = 3)
  private BigDecimal grossWeight;

  @Column(name = "location_id")
  private UUID locationId;

  @Column(name = "sequence_no", nullable = false)
  private int sequenceNo;

  @Column(name = "notes", columnDefinition = "text")
  private String notes;

  @Override
  protected String getModuleCode() {
    return "POUTI";
  }

  public static ProductionOutputItem create(
      PackageType packageType,
      BigDecimal netWeight,
      BigDecimal grossWeight,
      UUID locationId,
      int sequenceNo,
      String notes) {
    ProductionOutputItem item = new ProductionOutputItem();
    item.setPackageType(packageType);
    item.setNetWeight(netWeight);
    item.setGrossWeight(grossWeight);
    item.setLocationId(locationId);
    item.setSequenceNo(sequenceNo);
    item.setNotes(notes);
    return item;
  }
}
