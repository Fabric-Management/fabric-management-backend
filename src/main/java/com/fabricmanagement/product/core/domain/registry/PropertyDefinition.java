package com.fabricmanagement.product.core.domain.registry;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(
    name = "prod_property_definition",
    schema = "production",
    indexes = {
      @Index(name = "idx_property_definition_tenant_family", columnList = "tenant_id, unit_family")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_property_definition_tenant_key",
          columnNames = {"tenant_id", "property_key"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PropertyDefinition extends BaseEntity {

  @Column(name = "property_key", nullable = false, length = 100, updatable = false)
  private String propertyKey;

  @Column(name = "canonical_field_name", nullable = false, length = 100)
  private String canonicalFieldName;

  @Enumerated(EnumType.STRING)
  @Column(name = "semantic_role_default", nullable = false, length = 40)
  private SemanticRole semanticRoleDefault;

  @Column(name = "dimension", nullable = false, length = 100)
  private String dimension;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_type", nullable = false, length = 20)
  private PropertyDataType dataType;

  @Enumerated(EnumType.STRING)
  @Column(name = "unit_family", nullable = false, length = 40)
  private UnitFamily unitFamily;

  @Enumerated(EnumType.STRING)
  @Column(name = "canonical_unit_code", length = 40)
  private UnitCode canonicalUnitCode;

  @Type(JsonType.class)
  @Column(name = "allowed_unit_codes", columnDefinition = "jsonb", nullable = false)
  private List<UnitCode> allowedUnitCodes;

  @Column(name = "conversion_policy", length = 100)
  private String conversionPolicy;

  @Column(name = "rounding_policy", length = 100)
  private String roundingPolicy;

  @Column(name = "nominal_source", length = 100)
  private String nominalSource;

  @Column(name = "tolerance_source", length = 100)
  private String toleranceSource;

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "system_defined", nullable = false, updatable = false)
  private boolean systemDefined;

  public static PropertyDefinition from(PropertyDefinitionSpec spec) {
    PropertyDefinition definition = new PropertyDefinition();
    definition.propertyKey = spec.propertyKey();
    definition.canonicalFieldName = spec.canonicalFieldName();
    definition.semanticRoleDefault = spec.semanticRoleDefault();
    definition.dimension = spec.dimension();
    definition.dataType = spec.dataType();
    definition.unitFamily = spec.unitFamily();
    definition.canonicalUnitCode = spec.canonicalUnitCode();
    definition.allowedUnitCodes = List.copyOf(spec.allowedUnitCodes());
    definition.conversionPolicy = spec.conversionPolicy();
    definition.roundingPolicy = spec.roundingPolicy();
    definition.nominalSource = spec.nominalSource();
    definition.toleranceSource = spec.toleranceSource();
    definition.description = spec.description();
    definition.systemDefined = spec.systemDefined();
    return definition;
  }

  public PropertyDefinitionSpec toSpec() {
    return new PropertyDefinitionSpec(
        propertyKey,
        canonicalFieldName,
        semanticRoleDefault,
        dimension,
        dataType,
        unitFamily,
        canonicalUnitCode,
        allowedUnitCodes,
        conversionPolicy,
        roundingPolicy,
        nominalSource,
        toleranceSource,
        description,
        systemDefined);
  }

  public boolean contractEquals(PropertyDefinitionSpec expected) {
    return Objects.equals(toSpec(), expected);
  }

  @Override
  protected String getModuleCode() {
    return "PREG";
  }
}
