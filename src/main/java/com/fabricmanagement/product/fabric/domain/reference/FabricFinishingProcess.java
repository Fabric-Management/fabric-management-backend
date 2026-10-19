package com.fabricmanagement.product.fabric.domain.reference;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A tenant's name for a finishing process ("Mercerising", "Peach finish"), mapped to one closed
 * category (FABRIC-3a). Processes are recorded on lots, never on the fabric: one fabric becomes
 * greige, dyed or coated lots by what is done to each lot (F7). Code and category never change
 * after insert, because lots reason on them; name, description, charge unit and order are the
 * tenant's to edit.
 */
@Entity
@Table(
    name = "prod_fabric_finishing_process",
    schema = "production",
    indexes = {
      @Index(
          name = "idx_fabric_finishing_process_tenant_active",
          columnList = "tenant_id, is_active")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_fabric_finishing_process_tenant_code",
          columnNames = {"tenant_id", "code"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FabricFinishingProcess extends BaseEntity {

  private static final Pattern UPPER_SNAKE = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");

  @Column(name = "code", nullable = false, length = 50, updatable = false)
  private String code;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "display_order")
  private Integer displayOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 30, updatable = false)
  private FabricFinishingCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "charge_unit", nullable = false, length = 10)
  private FinishingChargeUnit chargeUnit;

  @Column(name = "system_defined", nullable = false, updatable = false)
  private boolean systemDefined;

  public static FabricFinishingProcess defineTenant(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      FabricFinishingCategory category,
      FinishingChargeUnit chargeUnit) {
    return create(tenantId, code, name, description, displayOrder, category, chargeUnit, false);
  }

  public static FabricFinishingProcess defineSystem(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      FabricFinishingCategory category,
      FinishingChargeUnit chargeUnit) {
    return create(tenantId, code, name, description, displayOrder, category, chargeUnit, true);
  }

  private static FabricFinishingProcess create(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      FabricFinishingCategory category,
      FinishingChargeUnit chargeUnit,
      boolean systemDefined) {
    if (tenantId == null) {
      throw new FabricDomainException("Finishing process tenantId must not be null");
    }
    if (category == null) {
      throw new FabricDomainException("A finishing process needs a category");
    }
    FabricFinishingProcess process = new FabricFinishingProcess();
    process.setTenantId(tenantId);
    process.code = requireCode(code);
    process.name = requireName(name);
    process.description = FabricConstruction.trimToNull(description);
    process.displayOrder = displayOrder;
    process.category = category;
    process.chargeUnit = requireChargeUnit(chargeUnit);
    process.systemDefined = systemDefined;
    return process;
  }

  /** Renames, re-prices by another unit or reorders the process; code and category stay. */
  public void update(
      String name,
      String description,
      Integer displayOrder,
      FinishingChargeUnit chargeUnit,
      boolean active) {
    this.name = requireName(name);
    this.description = FabricConstruction.trimToNull(description);
    this.displayOrder = displayOrder;
    this.chargeUnit = requireChargeUnit(chargeUnit);
    setIsActive(active);
  }

  public boolean isUsable() {
    return Boolean.TRUE.equals(getIsActive()) && getDeletedAt() == null;
  }

  private static String requireCode(String code) {
    if (code == null || !code.equals(code.trim()) || !UPPER_SNAKE.matcher(code).matches()) {
      throw new FabricDomainException(
          "A finishing process code is upper snake case, e.g. MERCERISING: " + code);
    }
    return code;
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new FabricDomainException("A finishing process needs a name");
    }
    return name.trim();
  }

  private static FinishingChargeUnit requireChargeUnit(FinishingChargeUnit chargeUnit) {
    if (chargeUnit == null) {
      throw new FabricDomainException("A finishing process is charged by metre or kilogram");
    }
    return chargeUnit;
  }

  @Override
  protected String getModuleCode() {
    return "FFIN";
  }
}
