package com.fabricmanagement.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.*;

/**
 * User entity representing a platform user.
 *
 * <p><b>CRITICAL DESIGN DECISIONS:</b>
 *
 * <ul>
 *   <li>❌ NO username field - Use Contact entity via UserContact junction
 *   <li>✅ displayName auto-generated from firstName + lastName
 *   <li>✅ Contacts managed via Contact entity and UserContact junction
 *   <li>✅ Every user belongs to a company (tenant)
 *   <li>✅ Department-based access control via UserDepartment junction
 * </ul>
 *
 * <h2>Multi-Tenancy:</h2>
 *
 * <p>User inherits tenant_id from BaseEntity. All queries MUST be tenant-scoped.
 *
 * <h2>Example:</h2>
 *
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
@Table(
    name = "common_user",
    schema = "common_user",
    indexes = {
      @Index(name = "idx_user_organization", columnList = "organization_id"),
      @Index(name = "idx_user_tenant", columnList = "tenant_id")
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

  /**
   * The organization this user belongs to.
   *
   * <p><b>V046 Migration:</b> Renamed from company_id to organization_id. FK now references
   * common_organization instead of common_company.
   */
  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "user_type", nullable = false, length = 20)
  @Builder.Default
  private UserType userType = UserType.INTERNAL;

  @Enumerated(EnumType.STRING)
  @Column(name = "trust_level", nullable = false, length = 50)
  @Builder.Default
  private com.fabricmanagement.approval.domain.UserTrustLevel trustLevel =
      com.fabricmanagement.approval.domain.UserTrustLevel.PROBATION;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "role_id")
  private Role role;

  @Column(name = "wip_limit")
  @Builder.Default
  private Integer wipLimit = 5;

  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<UserDepartment> userDepartments = new ArrayList<>();

  @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
  @Builder.Default
  private List<UserContact> userContacts = new ArrayList<>();

  @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
  @Builder.Default
  private List<UserAddress> userAddresses = new ArrayList<>();

  @Column(name = "last_active_at")
  private Instant lastActiveAt;

  @Column(name = "onboarding_completed_at")
  private Instant onboardingCompletedAt;

  /**
   * User's preferred locale tag (e.g. "tr-TR", "en-US").
   *
   * <p>Null = inherit from tenant settings (3-tier cascade: user → tenant → system default EN).
   */
  @Column(name = "preferred_locale", length = 10)
  private String preferredLocale;

  /**
   * User's preferred IANA timezone (e.g. "Europe/Istanbul", "UTC").
   *
   * <p>Null = inherit from tenant settings.
   */
  @Column(name = "preferred_timezone", length = 50)
  private String preferredTimezone;

  public static User create(String firstName, String lastName, UUID organizationId) {
    return create(firstName, lastName, organizationId, UserType.INTERNAL);
  }

  public static User create(
      String firstName, String lastName, UUID organizationId, UserType userType) {
    return User.builder()
        .firstName(firstName)
        .lastName(lastName)
        .organizationId(organizationId)
        .userType(userType)
        .build();
  }

  public void updateProfile(String firstName, String lastName) {
    this.firstName = firstName;
    this.lastName = lastName;
  }

  public String getDisplayName() {
    return this.firstName + " " + this.lastName;
  }

  public void updateLastActive() {
    this.lastActiveAt = Instant.now();
  }

  /**
   * Update locale and timezone preferences for this user.
   *
   * @param locale IETF BCP 47 language tag (e.g. "tr-TR"). Null clears override (inherits tenant).
   * @param timezone IANA timezone identifier (e.g. "Europe/Istanbul"). Null clears override.
   */
  public void updateLocalePreferences(String locale, String timezone) {
    this.preferredLocale = locale;
    this.preferredTimezone = timezone;
  }

  public boolean hasCompletedOnboarding() {
    return this.onboardingCompletedAt != null;
  }

  public void completeOnboarding() {
    this.onboardingCompletedAt = Instant.now();
  }

  /**
   * Get any verified contact for authentication.
   *
   * <p>Returns first verified contact found (any verified contact = authentication contact).
   */
  public Optional<com.fabricmanagement.platform.communication.domain.Contact>
      getAnyVerifiedContact() {
    return userContacts.stream()
        .filter(uc -> uc.getContact() != null)
        .map(UserContact::getContact)
        .filter(contact -> Boolean.TRUE.equals(contact.getIsVerified()))
        .findFirst();
  }

  /** Get default contact for notifications. */
  public Optional<com.fabricmanagement.platform.communication.domain.Contact> getDefaultContact() {
    return userContacts.stream()
        .filter(uc -> Boolean.TRUE.equals(uc.getIsDefault()))
        .findFirst()
        .map(UserContact::getContact);
  }

  /** Get primary address. */
  public Optional<com.fabricmanagement.platform.communication.domain.Address> getPrimaryAddress() {
    return userAddresses.stream()
        .filter(ua -> Boolean.TRUE.equals(ua.getIsPrimary()))
        .findFirst()
        .map(UserAddress::getAddress);
  }

  @Override
  protected String getModuleCode() {
    return "USER";
  }
}
