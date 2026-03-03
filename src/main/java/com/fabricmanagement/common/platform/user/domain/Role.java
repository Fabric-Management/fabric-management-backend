package com.fabricmanagement.common.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

/**
 * Role entity - Dynamic, database-driven role management.
 *
 * <p>Roles are generic and context-free. The department provides organizational context:
 *
 * <ul>
 *   <li>Role = WHAT the user can do (Admin, Manager, Worker, Viewer...)
 *   <li>Department = WHERE the user operates (Production, Logistics, HR...)
 *   <li>Combined: "Manager in Production" = Production Manager
 * </ul>
 *
 * <p><b>Role Scopes:</b>
 *
 * <ul>
 *   <li>INTERNAL — Tenant employee roles (ADMIN, MANAGER, SUPERVISOR, WORKER, VIEWER)
 *   <li>PARTNER — Trading partner roles (PARTNER_OWNER, PARTNER_ACCOUNTANT, PARTNER_BUYER,
 *       PARTNER_VIEWER)
 *   <li>SYSTEM — Platform roles, hidden from tenant UI (PLATFORM_ADMIN)
 * </ul>
 */
@Entity
@Table(
    name = "common_role",
    schema = "common_user",
    indexes = {
      @Index(name = "idx_role_tenant", columnList = "tenant_id"),
      @Index(name = "idx_role_code", columnList = "role_code"),
      @Index(name = "idx_role_active", columnList = "is_active"),
      @Index(name = "uk_role_tenant_code", columnList = "tenant_id,role_code", unique = true)
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role extends BaseEntity {

  @Column(name = "role_name", nullable = false, length = 100)
  private String roleName;

  @Column(name = "role_code", nullable = false, length = 50)
  private String roleCode;

  @Column(name = "description", length = 500)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "role_scope", nullable = false, length = 20)
  @Builder.Default
  private RoleScope roleScope = RoleScope.INTERNAL;

  @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = false)
  @Builder.Default
  private List<User> users = new ArrayList<>();

  public static Role create(
      String roleName, String roleCode, String description, RoleScope roleScope) {
    return Role.builder()
        .roleName(roleName)
        .roleCode(roleCode)
        .description(description)
        .roleScope(roleScope)
        .build();
  }

  public static Role create(String roleName, String roleCode, String description) {
    return create(roleName, roleCode, description, RoleScope.INTERNAL);
  }

  @Override
  protected String getModuleCode() {
    return "ROLE";
  }
}
