package com.fabricmanagement.common.platform.company.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.*;

/**
 * Company entity - DEPRECATED, use {@link
 * com.fabricmanagement.common.platform.organization.domain.Organization} instead.
 *
 * <p><b>MIGRATION NOTICE:</b> This entity is deprecated as of Faz 3 migration. The Company concept
 * has been split into:
 *
 * <ul>
 *   <li>{@link com.fabricmanagement.common.platform.tenant.domain.Tenant} - Platform-level
 *       subscription boundary
 *   <li>{@link com.fabricmanagement.common.platform.organization.domain.Organization} - Internal
 *       organizational structure
 *   <li>{@link TradingPartner} - External business partners (suppliers, customers)
 * </ul>
 *
 * <h2>Migration Path:</h2>
 *
 * <ol>
 *   <li>Replace CompanyRepository with OrganizationRepository
 *   <li>Replace CompanyService with OrganizationService
 *   <li>Use TenantService for tenant-level operations
 *   <li>Use TradingPartnerService for external partners
 * </ol>
 *
 * @deprecated Use {@link com.fabricmanagement.common.platform.organization.domain.Organization} for
 *     internal structure, {@link com.fabricmanagement.common.platform.tenant.domain.Tenant} for
 *     tenant operations, or {@link TradingPartner} for external partners.
 */
@Deprecated(since = "Faz 3 Migration", forRemoval = true)
@Entity
@Table(
    name = "common_organization", // Points to renamed table for backward compatibility
    schema = "common_company",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_organization_tenant_tax_id",
            columnNames = {"tenant_id", "tax_id"}))
@AttributeOverride(
    name = "tenantId",
    column = @Column(name = "tenant_id", nullable = false, updatable = false))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company extends BaseEntity {

  // Column renamed from company_name → name in V046
  @Column(name = "name", nullable = false, length = 255)
  private String companyName;

  @Column(name = "tax_id", nullable = false, length = 50)
  private String taxId;

  // Column renamed from company_type → organization_type in V046
  @Enumerated(EnumType.STRING)
  @Column(name = "organization_type", nullable = false)
  @Builder.Default
  private CompanyType companyType = CompanyType.VERTICAL_MILL;

  // Column renamed from parent_company_id → parent_organization_id in V046
  @Column(name = "parent_organization_id")
  private UUID parentCompanyId;

  @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
  @Builder.Default
  private List<CompanyContact> companyContacts = new ArrayList<>();

  @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
  @Builder.Default
  private List<CompanyAddress> companyAddresses = new ArrayList<>();

  /** Get default contact for business communication. */
  public Optional<com.fabricmanagement.common.platform.communication.domain.Contact>
      getDefaultContact() {
    return companyContacts.stream()
        .filter(cc -> Boolean.TRUE.equals(cc.getIsDefault()))
        .findFirst()
        .map(CompanyContact::getContact);
  }

  /** Get headquarters address. */
  public Optional<com.fabricmanagement.common.platform.communication.domain.Address>
      getHeadquartersAddress() {
    return companyAddresses.stream()
        .filter(ca -> Boolean.TRUE.equals(ca.getIsHeadquarters()))
        .findFirst()
        .map(CompanyAddress::getAddress);
  }

  /** Get primary address. */
  public Optional<com.fabricmanagement.common.platform.communication.domain.Address>
      getPrimaryAddress() {
    return companyAddresses.stream()
        .filter(ca -> Boolean.TRUE.equals(ca.getIsPrimary()))
        .findFirst()
        .map(CompanyAddress::getAddress);
  }

  public CompanyCategory getCategory() {
    return this.companyType.getCategory();
  }

  public boolean isTenant() {
    return this.companyType.isTenant();
  }

  public String[] getSuggestedOS() {
    return this.companyType.getSuggestedOS();
  }

  public static Company create(String companyName, String taxId, CompanyType companyType) {
    return Company.builder().companyName(companyName).taxId(taxId).companyType(companyType).build();
  }

  public void setParent(UUID parentCompanyId) {
    this.parentCompanyId = parentCompanyId;
  }

  public boolean hasParent() {
    return this.parentCompanyId != null;
  }

  public void update(String companyName, String taxId) {
    this.companyName = companyName;
    this.taxId = taxId;
  }

  @Override
  protected String getModuleCode() {
    return "COMP";
  }

  /**
   * Override UID generation for Company - Company UID = Tenant UID for root tenants.
   *
   * <p><b>CRITICAL:</b> For root tenant companies (tenant_id = company_id), Company UID should
   * equal Tenant UID (e.g., "ACME-001").
   *
   * <p><b>Special Case:</b> Root tenant companies use company name-based UID generation. This is
   * typically handled in TenantOnboardingService during tenant creation.
   *
   * <p><b>Default Behavior:</b> If UID is not pre-set (via setUid() in service layer), falls back
   * to standard BaseEntity pattern: {TENANT_UID}-COMP-{UUID_SUFFIX}
   *
   * <p>This allows:
   *
   * <ul>
   *   <li>Root tenant companies: Custom UID from company name (set via service)
   *   <li>Sub-companies: Standard pattern with COMP module code
   * </ul>
   */
  @Override
  protected String generateUid() {
    // ⚠️ CRITICAL: For root tenant companies, UID should be set BEFORE save
    // (typically in TenantOnboardingService.createTenantCompany()).
    // If not set, this method will be called by BaseEntity.onCreate().

    // For root tenants (tenant_id = id), we want format: {TENANT_UID}
    // But we don't have company name here during entity creation.
    // So we fall back to standard pattern for now.
    // The service layer (TenantOnboardingService) should set UID explicitly.

    // Fallback: Use standard pattern
    // This will be overridden by explicit setUid() call in service layer
    return super.generateUid();
  }
}
