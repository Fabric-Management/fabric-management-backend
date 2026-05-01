package com.fabricmanagement.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Represents a generic permission rule defined at the system or tenant level. */
@Entity
@Table(name = "permission_template")
@AttributeOverrides({
  @AttributeOverride(
      name = "tenantId",
      column = @Column(name = "tenant_id", nullable = true, updatable = false))
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionTemplate extends BaseEntity {

  @Column(name = "role_code", nullable = false, length = 50)
  private String roleCode;

  @Column(name = "department_code", length = 50)
  private String departmentCode;

  @Column(name = "resource", nullable = false, length = 50)
  private String resource;

  @Column(name = "action", nullable = false, length = 20)
  private String action;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_scope", nullable = false, length = 20)
  private DataScope dataScope;

  @Override
  protected String getModuleCode() {
    return "PRMT";
  }
}
