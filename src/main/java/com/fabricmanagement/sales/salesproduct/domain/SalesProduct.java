package com.fabricmanagement.sales.salesproduct.domain;

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
@Table(name = "sales_product", schema = "sales")
@Getter
@Setter
@NoArgsConstructor
public class SalesProduct extends BaseEntity {

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "product_name", length = 255)
  private String productName;

  @Column(name = "module_type", nullable = false, length = 50)
  private String moduleType;

  @Column(name = "list_price", nullable = false, precision = 18, scale = 4)
  private BigDecimal listPrice;

  @Column(name = "currency", nullable = false, length = 10)
  private String currency;

  @Column(name = "moq", precision = 15, scale = 3)
  private BigDecimal moq;

  @Column(name = "moq_unit", length = 20)
  private String moqUnit;

  @Column(name = "lead_time_days")
  private Integer leadTimeDays;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false)
  private String specs = "{}";

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false)
  private String photos = "[]";

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
