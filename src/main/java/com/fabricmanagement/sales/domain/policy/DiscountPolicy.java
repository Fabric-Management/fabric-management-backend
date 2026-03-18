package com.fabricmanagement.sales.domain.policy;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "discount_policy", schema = "sales")
@Getter
@Setter
@NoArgsConstructor
public class DiscountPolicy extends BaseEntity {

  @Column(name = "module_type", nullable = false, length = 50)
  private String moduleType;

  @Column(name = "base_discount_limit", nullable = false, precision = 5, scale = 4)
  private BigDecimal baseDiscountLimit;

  @Column(name = "min_profit_margin", nullable = false, precision = 5, scale = 4)
  private BigDecimal minProfitMargin;

  @Column(name = "require_manager_above", nullable = false, precision = 5, scale = 4)
  private BigDecimal requireManagerAbove;

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
