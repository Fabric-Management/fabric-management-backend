package com.fabricmanagement.product.fabric.domain.reference;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricConstructionFamily;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
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
 * A tenant's name for a fabric construction ("3/1 S twill", "Lacoste pique"), mapped to one closed
 * family (FABRIC-ARCHITECTURE F3). Code, family and formation never change after insert: articles
 * reason on them. Name, description and order are the tenant's to edit.
 */
@Entity
@Table(
    name = "prod_fabric_construction",
    schema = "production",
    indexes = {
      @Index(name = "idx_fabric_construction_tenant_active", columnList = "tenant_id, is_active")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_fabric_construction_tenant_code",
          columnNames = {"tenant_id", "code"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FabricConstruction extends BaseEntity {

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
  @Column(name = "formation", nullable = false, length = 20, updatable = false)
  private FabricFormation formation;

  @Enumerated(EnumType.STRING)
  @Column(name = "family", nullable = false, length = 30, updatable = false)
  private FabricConstructionFamily family;

  @Column(name = "system_defined", nullable = false, updatable = false)
  private boolean systemDefined;

  public static FabricConstruction defineTenant(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      FabricConstructionFamily family) {
    return create(tenantId, code, name, description, displayOrder, family, false);
  }

  public static FabricConstruction defineSystem(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      FabricConstructionFamily family) {
    return create(tenantId, code, name, description, displayOrder, family, true);
  }

  private static FabricConstruction create(
      UUID tenantId,
      String code,
      String name,
      String description,
      Integer displayOrder,
      FabricConstructionFamily family,
      boolean systemDefined) {
    if (tenantId == null) {
      throw new FabricDomainException("Fabric construction tenantId must not be null");
    }
    if (family == null) {
      throw new FabricDomainException("A fabric construction needs a family");
    }
    FabricConstruction construction = new FabricConstruction();
    construction.setTenantId(tenantId);
    construction.code = requireCode(code);
    construction.name = requireName(name);
    construction.description = trimToNull(description);
    construction.displayOrder = displayOrder;
    construction.family = family;
    construction.formation = family.formation();
    construction.systemDefined = systemDefined;
    return construction;
  }

  /** Renames or reorders the construction; code, family and formation stay as inserted. */
  public void update(String name, String description, Integer displayOrder, boolean active) {
    this.name = requireName(name);
    this.description = trimToNull(description);
    this.displayOrder = displayOrder;
    setIsActive(active);
  }

  public boolean isUsable() {
    return Boolean.TRUE.equals(getIsActive()) && getDeletedAt() == null;
  }

  static String requireCode(String code) {
    if (code == null || !code.equals(code.trim()) || !UPPER_SNAKE.matcher(code).matches()) {
      throw new FabricDomainException(
          "A fabric construction code is upper snake case, e.g. TWILL_3_1: " + code);
    }
    return code;
  }

  static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new FabricDomainException("A fabric construction needs a name");
    }
    return name.trim();
  }

  static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  @Override
  protected String getModuleCode() {
    return "FCON";
  }
}
