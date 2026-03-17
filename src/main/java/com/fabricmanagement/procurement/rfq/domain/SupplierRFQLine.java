package com.fabricmanagement.procurement.rfq.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "supplier_rfq_line", schema = "procurement")
@Getter
@Setter
@NoArgsConstructor
public class SupplierRFQLine extends BaseEntity {

  @Column(name = "rfq_id", nullable = false)
  private UUID rfqId;

  @Column(name = "material_id")
  private UUID materialId;

  @Column(name = "product_desc", columnDefinition = "TEXT")
  private String productDesc;

  @Column(name = "requested_qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal requestedQty;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Type(JsonType.class)
  @Column(name = "module_specs", columnDefinition = "jsonb", nullable = false)
  private String moduleSpecs = "{}";

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  public void markAsDeleted() {
    this.isActive = false;
    super.delete();
  }

  @Override
  public String getModuleCode() {
    return "PROCUREMENT";
  }
}
