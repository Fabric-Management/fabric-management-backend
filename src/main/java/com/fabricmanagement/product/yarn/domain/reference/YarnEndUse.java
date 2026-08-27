package com.fabricmanagement.product.yarn.domain.reference;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tenant-scoped, multi-select yarn end-use label. */
@Entity
@Table(
    name = "prod_yarn_end_use",
    schema = "production",
    indexes = {
      @Index(name = "idx_yarn_end_use_tenant_active", columnList = "tenant_id, is_active")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_yarn_end_use_tenant_code",
          columnNames = {"tenant_id", "code"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YarnEndUse extends BaseEntity {

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

  public static YarnEndUse defineTenant(
      UUID tenantId, String code, String name, String description, Integer displayOrder) {
    return create(tenantId, code, name, description, displayOrder, false);
  }

  public static YarnEndUse defineSystem(
      UUID tenantId, String code, String name, String description, Integer displayOrder) {
    return create(tenantId, code, name, description, displayOrder, true);
  }

  private static YarnEndUse create(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      boolean systemDefined) {
    YarnEndUse endUse = new YarnEndUse();
    endUse.setTenantId(YarnCatalogueRules.requireTenantId(tenantId));
    endUse.code = YarnCatalogueRules.requireCode(code);
    endUse.name = YarnCatalogueRules.requireName(name);
    endUse.description = YarnCatalogueRules.trimToNull(description);
    endUse.displayOrder = displayOrder;
    endUse.systemDefined = systemDefined;
    return endUse;
  }

  public void update(String code, String name, String description, Integer displayOrder) {
    YarnCatalogueRules.requireUnchanged("code", this.code, YarnCatalogueRules.requireCode(code));
    this.name = YarnCatalogueRules.requireName(name);
    this.description = YarnCatalogueRules.trimToNull(description);
    this.displayOrder = displayOrder;
  }

  @Override
  protected String getModuleCode() {
    return "YEND";
  }
}
