package com.fabricmanagement.product.fabric.domain.pricing;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A tier a subcontractor prices a dyeing by ("Light", "Dark", "Black", "Turquoise"), named by the
 * tenant (FABRIC-3c). Dyehouses tier by dye strength and list some colours as lines of their own,
 * so the tiers are the tenant's to define and which colour falls in which tier is what the firm
 * says; nothing here derives it from a colour. The code never changes after insert, because price
 * rows and finishing steps point at the class by id and read it by code.
 */
@Entity
@Table(
    name = "prod_fabric_finishing_price_class",
    schema = "production",
    indexes = {
      @Index(
          name = "idx_fabric_finishing_price_class_tenant_active",
          columnList = "tenant_id, is_active")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_fabric_finishing_price_class_tenant_code",
          columnNames = {"tenant_id", "code"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FabricFinishingPriceClass extends BaseEntity {

  private static final Pattern UPPER_SNAKE = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");

  @Column(name = "code", nullable = false, length = 50, updatable = false)
  private String code;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "display_order")
  private Integer displayOrder;

  @Column(name = "system_defined", nullable = false, updatable = false)
  private boolean systemDefined;

  public static FabricFinishingPriceClass defineTenant(
      UUID tenantId, String code, String name, String description, Integer displayOrder) {
    return create(tenantId, code, name, description, displayOrder, false);
  }

  public static FabricFinishingPriceClass defineSystem(
      UUID tenantId, String code, String name, String description, Integer displayOrder) {
    return create(tenantId, code, name, description, displayOrder, true);
  }

  private static FabricFinishingPriceClass create(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      boolean systemDefined) {
    if (tenantId == null) {
      throw new FabricDomainException("Finishing price class tenantId must not be null");
    }
    FabricFinishingPriceClass priceClass = new FabricFinishingPriceClass();
    priceClass.setTenantId(tenantId);
    priceClass.code = requireCode(code);
    priceClass.name = requireName(name);
    priceClass.description = trimToNull(description);
    priceClass.displayOrder = displayOrder;
    priceClass.systemDefined = systemDefined;
    return priceClass;
  }

  /** Renames, reorders or retires the class; the code stays. */
  public void update(String name, String description, Integer displayOrder, boolean active) {
    this.name = requireName(name);
    this.description = trimToNull(description);
    this.displayOrder = displayOrder;
    setIsActive(active);
  }

  public boolean isUsable() {
    return Boolean.TRUE.equals(getIsActive()) && getDeletedAt() == null;
  }

  private static String requireCode(String code) {
    if (code == null || !code.equals(code.trim()) || !UPPER_SNAKE.matcher(code).matches()) {
      throw new FabricDomainException(
          "A finishing price class code is upper snake case, e.g. EXTRA_DARK: " + code);
    }
    return code;
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new FabricDomainException("A finishing price class needs a name");
    }
    return name.trim();
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  @Override
  protected String getModuleCode() {
    return "FPCL";
  }
}
