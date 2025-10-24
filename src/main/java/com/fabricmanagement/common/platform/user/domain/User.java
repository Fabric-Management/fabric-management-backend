package com.fabricmanagement.common.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * User entity representing a platform user.
 *
 * <p><b>CRITICAL DESIGN DECISIONS:</b></p>
 * <ul>
 *   <li>❌ NO username field - Use contactValue (email/phone)</li>
 *   <li>✅ displayName auto-generated from firstName + lastName</li>
 *   <li>✅ contactValue is the primary identifier</li>
 *   <li>✅ Every user belongs to a company (tenant)</li>
 *   <li>✅ Department-based access control</li>
 * </ul>
 *
 * <h2>Multi-Tenancy:</h2>
 * <p>User inherits tenant_id from BaseEntity. All queries MUST be tenant-scoped.</p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * User user = User.builder()
 *     .firstName("John")
 *     .lastName("Doe")
 *     .contactValue("john.doe@acme.com")
 *     .contactType(ContactType.EMAIL)
 *     .companyId(company.getId())
 *     .department("production")
 *     .build();
 * // displayName = "John Doe" (auto)
 * // tenantId = auto from TenantContext
 * // uid = "ACME-001-USER-00042" (auto)
 * }</pre>
 */
@Entity
@Table(name = "common_user", schema = "common_user",
    indexes = {
        @Index(name = "idx_user_contact", columnList = "contact_value", unique = true),
        @Index(name = "idx_user_tenant_company", columnList = "tenant_id,company_id"),
        @Index(name = "idx_user_department", columnList = "department")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "contact_value", nullable = false, unique = true, length = 255)
    private String contactValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 20)
    private ContactType contactType;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (this.displayName == null || this.displayName.isBlank()) {
            this.displayName = generateDisplayName();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
        this.displayName = generateDisplayName();
    }

    private String generateDisplayName() {
        if (this.firstName == null || this.lastName == null) {
            return null;
        }
        return this.firstName + " " + this.lastName;
    }

    public static User create(String firstName, String lastName, String contactValue,
                             ContactType contactType, UUID companyId, String department) {
        return User.builder()
            .firstName(firstName)
            .lastName(lastName)
            .contactValue(contactValue)
            .contactType(contactType)
            .companyId(companyId)
            .department(department)
            .build();
    }

    public void updateProfile(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void updateLastActive() {
        this.lastActiveAt = Instant.now();
    }

    public void changeDepartment(String department) {
        this.department = department;
    }
}

