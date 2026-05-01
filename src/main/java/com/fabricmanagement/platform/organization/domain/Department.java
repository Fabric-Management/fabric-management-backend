package com.fabricmanagement.platform.organization.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.platform.user.domain.UserDepartment;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;

/**
 * Department within a company.
 *
 * <p>Represents organizational units within a company such as:
 *
 * <ul>
 *   <li>Production
 *   <li>Planning
 *   <li>Finance
 *   <li>Quality Control
 *   <li>Logistics
 * </ul>
 *
 * <h2>Usage in Authorization:</h2>
 *
 * <p>Department is used in Layer 3 of the Policy Engine to control access based on organizational
 * structure.
 *
 * <h2>Example:</h2>
 *
 * <pre>{@code
 * Department production = Department.builder()
 *     .organizationId(organization.getId())
 *     .departmentName("production")
 *     .description("Production Department")
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "common_department", schema = "common_company")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Department extends BaseEntity {

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "department_name", nullable = false, length = 100)
  private String departmentName;

  @Column(name = "department_code", nullable = false, length = 50)
  private String departmentCode;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "manager_id")
  private UUID managerId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_department_id")
  private Department parentDepartment;

  @Column(name = "is_system_department", nullable = false)
  @Builder.Default
  private Boolean isSystemDepartment = false;

  @Column(name = "display_order")
  private Integer displayOrder;

  @Column(name = "department_group", length = 20)
  private String departmentGroup;

  @OneToMany(mappedBy = "department", cascade = CascadeType.ALL, orphanRemoval = false)
  @Builder.Default
  private List<UserDepartment> userDepartments = new ArrayList<>();

  public static Department create(
      UUID organizationId, String departmentName, String departmentCode, String description) {
    return Department.builder()
        .organizationId(organizationId)
        .departmentName(departmentName)
        .departmentCode(departmentCode)
        .description(description)
        .build();
  }

  public void assignManager(UUID managerId) {
    this.managerId = managerId;
  }

  public void removeManager() {
    this.managerId = null;
  }

  public boolean hasManager() {
    return this.managerId != null;
  }

  @Override
  protected String getModuleCode() {
    return "DEPT";
  }
}
