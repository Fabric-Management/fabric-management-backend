package com.fabricmanagement.common.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Role entity - Dynamic, database-driven role management.
 *
 * <p>Represents user roles in the system. Unlike static enums, roles are
 * fully database-driven and can be managed through CRUD operations.</p>
 *
 * <p><b>Standard Roles (seeded):</b></p>
 * <ul>
 *   <li>ADMIN - Administrator (Full system access)</li>
 *   <li>DIRECTOR - Director (Üst yönetim erişimi)</li>
 *   <li>MANAGER - Manager (Departman yönetimi)</li>
 *   <li>SUPERVISOR - Supervisor (Vardiya / ekip lideri)</li>
 *   <li>USER - User (Standart çalışan)</li>
 *   <li>INTERN - Intern (Stajyer erişimi)</li>
 *   <li>VIEWER - Viewer (Sadece okuma yetkisi)</li>
 * </ul>
 *
 * <h2>Relationship:</h2>
 * <p>One Role can have Many Users (One-to-Many via User.roleId)</p>
 *
 * <h2>Usage in Authorization:</h2>
 * <p>Roles are referenced in Policy conditions by role_code (e.g., "ADMIN", "MANAGER").
 * This allows policies to remain stable even when role names change.</p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * Role admin = Role.builder()
 *     .roleName("Administrator")
 *     .roleCode("ADMIN")
 *     .description("Full system access")
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "common_role", schema = "common_user",
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

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    private List<User> users = new ArrayList<>();

    public static Role create(String roleName, String roleCode, String description) {
        return Role.builder()
            .roleName(roleName)
            .roleCode(roleCode)
            .description(description)
            .build();
    }

    @Override
    protected String getModuleCode() {
        return "ROLE";
    }
}

