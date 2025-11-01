package com.fabricmanagement.common.platform.user.domain;

import com.fabricmanagement.common.platform.company.domain.Department;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * User-Department junction entity (Many-to-Many relationship).
 *
 * <p>Represents the assignment of users to departments. A user can belong
 * to multiple departments, and each department can have multiple users.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Many-to-Many relationship between User and Department</li>
 *   <li>Primary department flag (isPrimary) - one department can be marked as primary</li>
 *   <li>Audit trail - tracks when and who assigned the relationship</li>
 * </ul>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * UserDepartment assignment = UserDepartment.builder()
 *     .user(user)
 *     .department(department)
 *     .isPrimary(true)
 *     .assignedBy(adminUserId)
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "common_user_department", schema = "common_user",
    indexes = {
        @Index(name = "idx_user_dept_user", columnList = "user_id"),
        @Index(name = "idx_user_dept_dept", columnList = "department_id"),
        @Index(name = "idx_user_dept_primary", columnList = "user_id,is_primary")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", insertable = false, updatable = false)
    private Department department;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "assigned_at", nullable = false)
    @Builder.Default
    private Instant assignedAt = Instant.now();

    @Column(name = "assigned_by")
    private UUID assignedBy;

    public static UserDepartment create(User user, Department department, boolean isPrimary, UUID assignedBy) {
        return UserDepartment.builder()
            .userId(user.getId())
            .departmentId(department.getId())
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
}

