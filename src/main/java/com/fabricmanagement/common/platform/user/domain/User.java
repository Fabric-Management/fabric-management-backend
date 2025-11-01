package com.fabricmanagement.common.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * User entity representing a platform user.
 *
 * <p><b>CRITICAL DESIGN DECISIONS:</b></p>
 * <ul>
 *   <li>❌ NO username field - Use Contact entity via UserContact junction</li>
 *   <li>✅ displayName auto-generated from firstName + lastName</li>
 *   <li>✅ Contacts managed via Contact entity and UserContact junction</li>
 *   <li>✅ Every user belongs to a company (tenant)</li>
 *   <li>✅ Department-based access control via UserDepartment junction</li>
 * </ul>
 *
 * <h2>Multi-Tenancy:</h2>
 * <p>User inherits tenant_id from BaseEntity. All queries MUST be tenant-scoped.</p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * User user = User.create("John", "Doe", company.getId());
 * // displayName = "John Doe" (auto)
 * // tenantId = auto from TenantContext
 * // uid = "ACME-001-USER-00042" (auto)
 * 
 * // Add contact via UserContactService
 * // Add departments via UserDepartmentService
 * }</pre>
 */
@Entity
@Table(name = "common_user", schema = "common_user",
    indexes = {
        @Index(name = "idx_user_tenant_company", columnList = "tenant_id,company_id")
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

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserDepartment> userDepartments = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Builder.Default
    private List<com.fabricmanagement.common.platform.communication.domain.UserContact> userContacts = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Builder.Default
    private List<com.fabricmanagement.common.platform.communication.domain.UserAddress> userAddresses = new ArrayList<>();

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

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

    public static User create(String firstName, String lastName, UUID companyId) {
        return User.builder()
            .firstName(firstName)
            .lastName(lastName)
            .companyId(companyId)
            .build();
    }

    public void updateProfile(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void updateLastActive() {
        this.lastActiveAt = Instant.now();
    }


    public boolean hasCompletedOnboarding() {
        return this.onboardingCompletedAt != null;
    }

    public void completeOnboarding() {
        this.onboardingCompletedAt = Instant.now();
    }

    /**
     * Get primary contact for authentication.
     * <p>Returns verified contact that is enabled for authentication.</p>
     */
    public Optional<com.fabricmanagement.common.platform.communication.domain.Contact> getPrimaryContact() {
        return userContacts.stream()
            .filter(uc -> Boolean.TRUE.equals(uc.getIsForAuthentication()))
            .filter(uc -> uc.getContact() != null && Boolean.TRUE.equals(uc.getContact().getIsVerified()))
            .findFirst()
            .map(com.fabricmanagement.common.platform.communication.domain.UserContact::getContact);
    }

    /**
     * Get default contact for notifications.
     */
    public Optional<com.fabricmanagement.common.platform.communication.domain.Contact> getDefaultContact() {
        return userContacts.stream()
            .filter(uc -> Boolean.TRUE.equals(uc.getIsDefault()))
            .findFirst()
            .map(com.fabricmanagement.common.platform.communication.domain.UserContact::getContact);
    }

    /**
     * Get primary address.
     */
    public Optional<com.fabricmanagement.common.platform.communication.domain.Address> getPrimaryAddress() {
        return userAddresses.stream()
            .filter(ua -> Boolean.TRUE.equals(ua.getIsPrimary()))
            .findFirst()
            .map(com.fabricmanagement.common.platform.communication.domain.UserAddress::getAddress);
    }

    @Override
    protected String getModuleCode() {
        return "USER";
    }
}

