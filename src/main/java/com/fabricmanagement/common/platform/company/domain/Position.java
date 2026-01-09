package com.fabricmanagement.common.platform.company.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.platform.user.domain.Role;
import com.fabricmanagement.common.platform.user.domain.UserPosition;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;

/**
 * Position entity - Job positions within departments.
 *
 * <p>Represents job positions/titles within a department. Each position:
 *
 * <ul>
 *   <li>Belongs to a specific department
 *   <li>Can have a default role assignment
 *   <li>Is tenant-isolated (multi-tenant support)
 *   <li>Can be assigned to multiple users (Many-to-Many via UserPosition)
 * </ul>
 *
 * <h2>Relationship:</h2>
 *
 * <ul>
 *   <li>Many Positions belong to One Department (Many-to-One)
 *   <li>One Position can have a default Role (Many-to-One, optional)
 *   <li>Many Users can have Many Positions (Many-to-Many via UserPosition)
 * </ul>
 *
 * <h2>Example:</h2>
 *
 * <pre>{@code
 * Position manager = Position.builder()
 *     .departmentId(department.getId())
 *     .positionName("Production Manager")
 *     .positionCode("PROD-MGR")
 *     .defaultRoleId(managerRole.getId())
 *     .description("Manages production department")
 *     .build();
 * }</pre>
 *
 * <h2>Position Hierarchy:</h2>
 *
 * <p>Positions can have a hierarchical parent to establish organizational structure (e.g., "Manager
 * → Supervisor → Operator"). This enables organizational chart visualization and hierarchical
 * reporting.
 *
 * <h2>Position Assignment History:</h2>
 *
 * <p>UserPosition junction tracks assignment dates via effective_date and end_date, enabling job
 * change history and career progression analysis.
 */
@Entity
@Table(
    name = "common_position",
    schema = "common_company",
    indexes = {
      @Index(name = "idx_position_tenant", columnList = "tenant_id"),
      @Index(name = "idx_position_department", columnList = "department_id"),
      @Index(name = "idx_position_active", columnList = "is_active"),
      @Index(name = "idx_position_display_order", columnList = "display_order"),
      @Index(
          name = "uk_position_tenant_code",
          columnList = "tenant_id,position_code",
          unique = true)
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position extends BaseEntity {

  @Column(name = "department_id", nullable = false)
  private UUID departmentId;

  @Column(name = "position_name", nullable = false, length = 100)
  private String positionName;

  @Column(name = "position_code", length = 50)
  private String positionCode;

  @Column(name = "description", length = 500)
  private String description;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id", insertable = false, updatable = false)
  private Department department; // Read-only relationship (departmentId is writable)

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "default_role_id")
  private Role defaultRole;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "hierarchical_parent_id")
  private Position hierarchicalParent;

  @Column(name = "display_order")
  @Builder.Default
  private Integer displayOrder = 0;

  @OneToMany(mappedBy = "position", cascade = CascadeType.ALL, orphanRemoval = false)
  @Builder.Default
  private List<UserPosition> userPositions = new ArrayList<>();

  @OneToMany(mappedBy = "hierarchicalParent", fetch = FetchType.LAZY)
  @Builder.Default
  private List<Position> childPositions = new ArrayList<>();

  public static Position create(
      UUID departmentId, String positionName, String positionCode, String description) {
    return Position.builder()
        .departmentId(departmentId)
        .positionName(positionName)
        .positionCode(positionCode)
        .description(description)
        .build();
  }

  public void assignDefaultRole(Role role) {
    this.defaultRole = role;
  }

  public void removeDefaultRole() {
    this.defaultRole = null;
  }

  public void assignParent(Position parent) {
    this.hierarchicalParent = parent;
  }

  public void removeParent() {
    this.hierarchicalParent = null;
  }

  public boolean hasParent() {
    return this.hierarchicalParent != null;
  }

  @Override
  protected String getModuleCode() {
    return "POS";
  }
}
