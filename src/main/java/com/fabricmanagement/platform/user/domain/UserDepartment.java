package com.fabricmanagement.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.organization.domain.Department;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * User-Department junction entity (Many-to-Many relationship).
 *
 * <p>Represents the assignment of users to departments. A user can belong to multiple departments,
 * and each department can have multiple users.
 *
 * <h2>Features:</h2>
 *
 * <ul>
 *   <li>Many-to-Many relationship between User and Department
 *   <li>Primary department flag (isPrimary) - one department can be marked as primary
 *   <li>Tenant isolation via tenant_id
 *   <li>Audit trail - tracks when and who assigned the relationship
 * </ul>
 */
@Entity
@Table(
    name = "common_user_department",
    schema = "common_user",
    indexes = {
      @Index(name = "idx_user_dept_user", columnList = "user_id"),
      @Index(name = "idx_user_dept_dept", columnList = "department_id"),
      @Index(name = "idx_user_dept_primary", columnList = "user_id,is_primary"),
      @Index(name = "idx_user_dept_tenant", columnList = "tenant_id"),
      @Index(name = "idx_user_dept_tenant_user", columnList = "tenant_id,user_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserDepartmentId.class)
public class UserDepartment {

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Id
  @Column(name = "department_id", nullable = false)
  private UUID departmentId;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id", insertable = false, updatable = false)
  private Department department;

  @Column(name = "is_primary", nullable = false)
  @Builder.Default
  private Boolean isPrimary = false;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private Boolean isActive = true;

  @Column(name = "assigned_at", nullable = false)
  @Builder.Default
  private Instant assignedAt = Instant.now();

  @Column(name = "assigned_by")
  private UUID assignedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  public static UserDepartment create(
      User user, Department department, boolean isPrimary, UUID assignedBy) {
    return UserDepartment.builder()
        .userId(user.getId())
        .departmentId(department.getId())
        .tenantId(TenantContext.getCurrentTenantId())
        .user(user)
        .department(department)
        .isPrimary(isPrimary)
        .assignedBy(assignedBy)
        .assignedAt(Instant.now())
        .build();
  }

  public void markAsPrimary() {
    this.isPrimary = true;
  }

  public void markAsSecondary() {
    this.isPrimary = false;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
