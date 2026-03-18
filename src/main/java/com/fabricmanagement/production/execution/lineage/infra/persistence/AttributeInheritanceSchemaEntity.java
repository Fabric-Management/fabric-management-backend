package com.fabricmanagement.production.execution.lineage.infra.persistence;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "inheritance_rule_schema", schema = "production")
@Getter
@Setter
@NoArgsConstructor
public class AttributeInheritanceSchemaEntity extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private MaterialType sourceType;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 30)
  private MaterialType targetType;

  @Type(JsonType.class)
  @Column(name = "rules", columnDefinition = "jsonb", nullable = false)
  private String rulesJson = "[]";

  @Override
  protected String getModuleCode() {
    return "LINEAGE";
  }
}
