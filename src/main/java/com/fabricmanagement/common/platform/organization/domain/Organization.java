package com.fabricmanagement.common.platform.organization.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.*;

/**
 * Organization entity - Internal organizational structure.
 *
 * <p><b>Replaces Company entity</b> after Tenant extraction. Organization represents the internal
 * structure of a tenant (departments, branches, hierarchy).
 *
 * <h2>Architecture:</h2>
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Tenant (Platform-level)                                        │
 * │  └── subscription, settings, billing                            │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  Organization (Tenant-level) ← THIS ENTITY                      │
 * │  ├── name, tax_id, organization_type                            │
 * │  ├── parent_organization_id (hierarchy)                         │
 * │  └── contacts, addresses                                        │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  TradingPartner (External)                                      │
 * │  └── suppliers, customers, service providers                    │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Key Changes from Company:</h2>
 *
 * <ul>
 *   <li>tenant_id now references Tenant.id (not self-referencing)
 *   <li>OrganizationType replaces CompanyType (no partner types)
 *   <li>Renamed fields: company_name → name, parent_company_id → parent_organization_id
 * </ul>
 *
 * <h2>Example:</h2>
 *
 * <pre>{@code
 * Organization org = Organization.create("ACME Textile", "1234567890", OrganizationType.VERTICAL_MILL);
 * // tenant_id = set from TenantContext (references Tenant entity)
 * }</pre>
 */
@Entity
@Table(
    name = "common_organization",
    schema = "common_company",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_organization_tenant_tax_id",
            columnNames = {"tenant_id", "tax_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization extends BaseEntity {

  /** Organization display name */
  @Column(name = "name", nullable = false, length = 255)
  private String name;

  /** Tax identification number - unique within tenant */
  @Column(name = "tax_id", nullable = false, length = 50)
  private String taxId;

  /** Organization type - determines suggested OS subscriptions */
  @Enumerated(EnumType.STRING)
  @Column(name = "organization_type", nullable = false)
  @Builder.Default
  private OrganizationType organizationType = OrganizationType.VERTICAL_MILL;

  /** Legal registered name (e.g. for invoicing) */
  @Column(name = "legal_name", length = 200)
  private String legalName;

  /** Trade registry / chamber registration number */
  @Column(name = "registration_number", length = 100)
  private String registrationNumber;

  /** Industry sector (e.g. TEXTILE, LOGISTICS) */
  @Column(name = "industry", length = 100)
  private String industry;

  /** Company website URL */
  @Column(name = "website", length = 500)
  private String website;

  /** Short company description */
  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  /** Parent organization for hierarchy (departments, branches) */
  @Column(name = "parent_organization_id")
  private UUID parentOrganizationId;

  /** Associated contacts (junction table) */
  @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY)
  @Builder.Default
  private List<OrganizationContact> organizationContacts = new ArrayList<>();

  /** Associated addresses (junction table) */
  @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY)
  @Builder.Default
  private List<OrganizationAddress> organizationAddresses = new ArrayList<>();

  // ========================================
  // FACTORY METHODS
  // ========================================

  /**
   * Create a new organization.
   *
   * <p>Maps name to both display name and legalName so that organizationName from self-signup or
   * sales-led onboarding is consistently stored in both fields.
   *
   * @param name Display name (also used as legalName when not separately provided)
   * @param taxId Tax ID
   * @param organizationType Type
   * @return new Organization
   */
  public static Organization create(String name, String taxId, OrganizationType organizationType) {
    return Organization.builder()
        .name(name)
        .legalName(name)
        .taxId(taxId)
        .organizationType(organizationType)
        .build();
  }

  // ========================================
  // BUSINESS METHODS
  // ========================================

  /**
   * Set parent organization for hierarchy.
   *
   * @param parentOrganizationId Parent UUID
   */
  public void setParent(UUID parentOrganizationId) {
    this.parentOrganizationId = parentOrganizationId;
  }

  /**
   * Check if this organization has a parent.
   *
   * @return true if has parent
   */
  public boolean hasParent() {
    return this.parentOrganizationId != null;
  }

  /**
   * Update organization basic info.
   *
   * @param name New name
   * @param taxId New tax ID
   */
  public void update(String name, String taxId) {
    setName(name);
    this.taxId = taxId;
  }

  /**
   * Set organization display name.
   *
   * <p>Safeguard: If legalName is null or empty when name is set, automatically assigns the same
   * value to legalName. Ensures self-signup organizationName is consistently reflected in both
   * fields even when only name is updated.
   *
   * @param name Display name
   */
  public void setName(String name) {
    this.name = name;
    if (name != null && !name.isBlank() && (this.legalName == null || this.legalName.isBlank())) {
      this.legalName = name;
    }
  }

  /**
   * Enrich organization with onboarding profile data.
   *
   * <p>Called during self-service onboarding completion to persist company profile fields that were
   * not available at registration time. Only non-null values overwrite existing data.
   *
   * @param legalName Legal registered name
   * @param registrationNumber Trade registry number
   * @param industry Industry sector
   * @param website Company website URL
   * @param description Short company description
   */
  public void enrich(
      String legalName,
      String registrationNumber,
      String industry,
      String website,
      String description) {
    if (legalName != null) this.legalName = legalName;
    if (registrationNumber != null) this.registrationNumber = registrationNumber;
    if (industry != null) this.industry = industry;
    if (website != null) this.website = website;
    if (description != null) this.description = description;
  }

  /**
   * Get suggested OS codes for this organization.
   *
   * @return suggested OS codes
   */
  public String[] getSuggestedOS() {
    return this.organizationType.getSuggestedOS();
  }

  // ========================================
  // CONTACT/ADDRESS HELPERS
  // ========================================

  /**
   * Get default contact for business communication.
   *
   * @return default contact if exists
   */
  public Optional<com.fabricmanagement.common.platform.communication.domain.Contact>
      getDefaultContact() {
    return organizationContacts.stream()
        .filter(oc -> Boolean.TRUE.equals(oc.getIsDefault()))
        .findFirst()
        .map(OrganizationContact::getContact);
  }

  /**
   * Get headquarters address.
   *
   * @return headquarters address if exists
   */
  public Optional<com.fabricmanagement.common.platform.communication.domain.Address>
      getHeadquartersAddress() {
    return organizationAddresses.stream()
        .filter(oa -> Boolean.TRUE.equals(oa.getIsHeadquarters()))
        .findFirst()
        .map(OrganizationAddress::getAddress);
  }

  /**
   * Get primary address.
   *
   * @return primary address if exists
   */
  public Optional<com.fabricmanagement.common.platform.communication.domain.Address>
      getPrimaryAddress() {
    return organizationAddresses.stream()
        .filter(oa -> Boolean.TRUE.equals(oa.getIsPrimary()))
        .findFirst()
        .map(OrganizationAddress::getAddress);
  }

  @Override
  protected String getModuleCode() {
    return "ORG";
  }
}
