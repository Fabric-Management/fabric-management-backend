package com.fabricmanagement.common.platform.company.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.*;

/**
 * Company entity representing a tenant in the multi-tenant system.
 *
 * <p>Company is the primary tenant boundary. Each company:
 *
 * <ul>
 *   <li>Has its own data isolation via tenant_id
 *   <li>Can have multiple OS subscriptions
 *   <li>Can have departments and users
 *   <li>Can have parent-child relationships
 *   <li>Can have commercial relationships (fason agreements)
 * </ul>
 *
 * <h2>Multi-Tenancy:</h2>
 *
 * <p>Company itself IS the tenant. The tenant_id field represents the company's UUID. For
 * hierarchical tenants (parent-child), both share same tenant_id but have different company IDs.
 *
 * <h2>Special Case - ROOT Tenant:</h2>
 *
 * <p><b>CRITICAL:</b> For ROOT tenant companies, tenant_id = company_id (self-referencing). This is
 * the ONLY entity where tenant_id is updatable.
 *
 * <h2>Example:</h2>
 *
 * <pre>{@code
 * Company acme = Company.builder()
 *     .companyName("ACME Corporation")
 *     .taxId("1234567890")
 *     .companyType(CompanyType.MANUFACTURER)
 *     .build();
 * // tenantId = auto-set to company's UUID
 * // uid = "ACME-001" (auto-generated)
 * }</pre>
 */
@Entity
@Table(name = "common_company", schema = "common_company")
@AttributeOverride(
    name = "tenantId",
    column = @Column(name = "tenant_id", nullable = false, updatable = true))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company extends BaseEntity {

  @Column(name = "company_name", nullable = false, length = 255)
  private String companyName;

  @Column(name = "tax_id", nullable = false, unique = true, length = 50)
  private String taxId;

  @Enumerated(EnumType.STRING)
  @Column(name = "company_type", nullable = false)
  @Builder.Default
  private CompanyType companyType = CompanyType.VERTICAL_MILL;

  @Column(name = "parent_company_id")
  private UUID parentCompanyId;

  @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
  @Builder.Default
  private List<com.fabricmanagement.common.platform.communication.domain.CompanyContact>
      companyContacts = new ArrayList<>();

  @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
  @Builder.Default
  private List<com.fabricmanagement.common.platform.communication.domain.CompanyAddress>
      companyAddresses = new ArrayList<>();

  /** Get default contact for business communication. */
  public Optional<com.fabricmanagement.common.platform.communication.domain.Contact>
      getDefaultContact() {
    return companyContacts.stream()
        .filter(cc -> Boolean.TRUE.equals(cc.getIsDefault()))
        .findFirst()
        .map(com.fabricmanagement.common.platform.communication.domain.CompanyContact::getContact);
  }

  /** Get headquarters address. */
  public Optional<com.fabricmanagement.common.platform.communication.domain.Address>
      getHeadquartersAddress() {
    return companyAddresses.stream()
        .filter(ca -> Boolean.TRUE.equals(ca.getIsHeadquarters()))
        .findFirst()
        .map(com.fabricmanagement.common.platform.communication.domain.CompanyAddress::getAddress);
  }

  /** Get primary address. */
  public Optional<com.fabricmanagement.common.platform.communication.domain.Address>
      getPrimaryAddress() {
    return companyAddresses.stream()
        .filter(ca -> Boolean.TRUE.equals(ca.getIsPrimary()))
        .findFirst()
        .map(com.fabricmanagement.common.platform.communication.domain.CompanyAddress::getAddress);
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
