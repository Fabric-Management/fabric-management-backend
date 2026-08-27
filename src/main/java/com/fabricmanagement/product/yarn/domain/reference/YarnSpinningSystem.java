package com.fabricmanagement.product.yarn.domain.reference;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tenant label for a spinning system, mapped to one canonical technology family. */
@Entity
@Table(
    name = "prod_yarn_spinning_system",
    schema = "production",
    indexes = {
      @Index(name = "idx_yarn_spinning_system_tenant_active", columnList = "tenant_id, is_active")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_yarn_spinning_system_tenant_code",
          columnNames = {"tenant_id", "code"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YarnSpinningSystem extends BaseEntity {

  @Column(name = "code", nullable = false, length = 50, updatable = false)
  private String code;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "display_order")
  private Integer displayOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "technology_family", nullable = false, length = 30, updatable = false)
  private SpinningTechnologyFamily technologyFamily;

  @Column(name = "system_defined", nullable = false, updatable = false)
  private boolean systemDefined;

  public static YarnSpinningSystem defineTenant(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      SpinningTechnologyFamily technologyFamily) {
    return create(tenantId, code, name, description, displayOrder, technologyFamily, false);
  }

  public static YarnSpinningSystem defineSystem(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      SpinningTechnologyFamily technologyFamily) {
    return create(tenantId, code, name, description, displayOrder, technologyFamily, true);
  }

  private static YarnSpinningSystem create(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      SpinningTechnologyFamily technologyFamily,
      boolean systemDefined) {
    if (technologyFamily == null) {
      throw new YarnDomainException("Spinning system technologyFamily must not be null");
    }
    YarnSpinningSystem system = new YarnSpinningSystem();
    system.setTenantId(YarnCatalogueRules.requireTenantId(tenantId));
    system.code = YarnCatalogueRules.requireCode(code);
    system.name = YarnCatalogueRules.requireName(name);
    system.description = YarnCatalogueRules.trimToNull(description);
    system.displayOrder = displayOrder;
    system.technologyFamily = technologyFamily;
    system.systemDefined = systemDefined;
    return system;
  }

  public void update(
      String code,
      String name,
      String description,
      Integer displayOrder,
      SpinningTechnologyFamily technologyFamily) {
    YarnCatalogueRules.requireUnchanged("code", this.code, YarnCatalogueRules.requireCode(code));
    YarnCatalogueRules.requireUnchanged(
        "technologyFamily", this.technologyFamily, technologyFamily);
    this.name = YarnCatalogueRules.requireName(name);
    this.description = YarnCatalogueRules.trimToNull(description);
    this.displayOrder = displayOrder;
  }

  @Override
  protected String getModuleCode() {
    return "YSPN";
  }
}
