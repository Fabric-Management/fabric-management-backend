package com.fabricmanagement.product.yarn.domain.reference;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Version-stable test method or instrument alias.
 *
 * <p>Applicability points to the Property Registry rather than a method pseudo-enum. Uster H and
 * Zweigle S3 hairiness methods are intentionally absent until YARN-5; when introduced they remain
 * separate rows because their measurements are uncorrelated and must never be merged.
 */
@Entity
@Table(
    name = "prod_yarn_test_method",
    schema = "production",
    indexes = {
      @Index(name = "idx_yarn_test_method_tenant_active", columnList = "tenant_id, is_active"),
      @Index(
          name = "idx_yarn_test_method_tenant_property",
          columnList = "tenant_id, applicable_property_key")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_yarn_test_method_tenant_code",
          columnNames = {"tenant_id", "code"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YarnTestMethod extends BaseEntity {

  @Column(name = "code", nullable = false, length = 50, updatable = false)
  private String code;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "display_order")
  private Integer displayOrder;

  @Column(name = "standard_ref", length = 100, updatable = false)
  private String standardRef;

  @Column(name = "instrument", length = 100, updatable = false)
  private String instrument;

  @Column(name = "applicable_property_key", length = 100, updatable = false)
  private String applicablePropertyKey;

  @Column(name = "system_defined", nullable = false, updatable = false)
  private boolean systemDefined;

  public static YarnTestMethod defineTenant(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      String standardRef,
      String instrument,
      String applicablePropertyKey) {
    return create(
        tenantId,
        code,
        name,
        description,
        displayOrder,
        standardRef,
        instrument,
        applicablePropertyKey,
        false);
  }

  public static YarnTestMethod defineSystem(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      String standardRef,
      String instrument,
      String applicablePropertyKey) {
    return create(
        tenantId,
        code,
        name,
        description,
        displayOrder,
        standardRef,
        instrument,
        applicablePropertyKey,
        true);
  }

  private static YarnTestMethod create(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      String standardRef,
      String instrument,
      String applicablePropertyKey,
      boolean systemDefined) {
    String normalizedStandard = YarnCatalogueRules.trimToNull(standardRef);
    String normalizedInstrument = YarnCatalogueRules.trimToNull(instrument);
    if (normalizedInstrument != null && normalizedStandard == null) {
      throw new YarnDomainException("A test method instrument requires standardRef");
    }
    YarnTestMethod method = new YarnTestMethod();
    method.setTenantId(YarnCatalogueRules.requireTenantId(tenantId));
    method.code = YarnCatalogueRules.requireCode(code);
    method.name = YarnCatalogueRules.requireName(name);
    method.description = YarnCatalogueRules.trimToNull(description);
    method.displayOrder = displayOrder;
    method.standardRef = normalizedStandard;
    method.instrument = normalizedInstrument;
    method.applicablePropertyKey = YarnCatalogueRules.trimToNull(applicablePropertyKey);
    method.systemDefined = systemDefined;
    return method;
  }

  public void update(
      String code,
      String name,
      String description,
      Integer displayOrder,
      String standardRef,
      String instrument,
      String applicablePropertyKey) {
    YarnCatalogueRules.requireUnchanged("code", this.code, YarnCatalogueRules.requireCode(code));
    YarnCatalogueRules.requireUnchanged(
        "standardRef", this.standardRef, YarnCatalogueRules.trimToNull(standardRef));
    YarnCatalogueRules.requireUnchanged(
        "instrument", this.instrument, YarnCatalogueRules.trimToNull(instrument));
    YarnCatalogueRules.requireUnchanged(
        "applicablePropertyKey",
        this.applicablePropertyKey,
        YarnCatalogueRules.trimToNull(applicablePropertyKey));
    this.name = YarnCatalogueRules.requireName(name);
    this.description = YarnCatalogueRules.trimToNull(description);
    this.displayOrder = displayOrder;
  }

  @Override
  protected String getModuleCode() {
    return "YTST";
  }
}
