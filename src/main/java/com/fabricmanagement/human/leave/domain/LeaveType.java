package com.fabricmanagement.human.leave.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "human_leave_type",
    schema = "human",
    indexes = {
      @Index(name = "idx_leave_type_country", columnList = "tenant_id,country_code"),
      @Index(name = "idx_leave_type_active", columnList = "tenant_id,is_active")
    })
@Getter
@Setter
@NoArgsConstructor
public class LeaveType extends BaseEntity {

  @Column(name = "code", nullable = false, length = 50)
  private String code;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "country_code", length = 8)
  private String countryCode;

  @Column(name = "statutory", nullable = false)
  private boolean statutory;

  @Column(name = "accrual_strategy", nullable = false, length = 150)
  private String accrualStrategy;

  @Column(name = "default_accrual_rate")
  private BigDecimal defaultAccrualRate;

  @Column(name = "max_carry_over")
  private BigDecimal maxCarryOver;

  @Column(name = "attributes", columnDefinition = "jsonb")
  private String attributes;

  @Builder
  public LeaveType(
      String code,
      String name,
      String description,
      String countryCode,
      boolean statutory,
      String accrualStrategy,
      BigDecimal defaultAccrualRate,
      BigDecimal maxCarryOver,
      String attributes,
      boolean active) {
    this.code = code;
    this.name = name;
    this.description = description;
    this.countryCode = countryCode;
    this.statutory = statutory;
    this.accrualStrategy = accrualStrategy;
    this.defaultAccrualRate = defaultAccrualRate;
    this.maxCarryOver = maxCarryOver;
    this.attributes = attributes;
    this.setIsActive(active);
  }

  public void deactivate() {
    this.setIsActive(false);
  }

  public boolean appliesToCountry(String country) {
    if (countryCode == null || countryCode.isBlank()) {
      return true;
    }
    return countryCode.equalsIgnoreCase(country);
  }

  @Override
  protected String getModuleCode() {
    return "LVT";
  }

  public boolean isActive() {
    return Boolean.TRUE.equals(getIsActive());
  }
}
