package com.fabricmanagement.common.platform.company.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Company entity representing a tenant in the multi-tenant system.
 *
 * <p>Company is the primary tenant boundary. Each company:
 * <ul>
 *   <li>Has its own data isolation via tenant_id</li>
 *   <li>Can have multiple OS subscriptions</li>
 *   <li>Can have departments and users</li>
 *   <li>Can have parent-child relationships</li>
 *   <li>Can have commercial relationships (fason agreements)</li>
 * </ul>
 *
 * <h2>Multi-Tenancy:</h2>
 * <p>Company itself IS the tenant. The tenant_id field represents the company's UUID.
 * For hierarchical tenants (parent-child), both share same tenant_id but have
 * different company IDs.</p>
 *
 * <h2>Special Case - ROOT Tenant:</h2>
 * <p><b>CRITICAL:</b> For ROOT tenant companies, tenant_id = company_id (self-referencing).
 * This is the ONLY entity where tenant_id is updatable.</p>
 *
 * <h2>Example:</h2>
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
@AttributeOverride(name = "tenantId", column = @Column(name = "tenant_id", nullable = false, updatable = true))
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

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "email", length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_type", nullable = false)
    @Builder.Default
    private CompanyType companyType = CompanyType.VERTICAL_MILL;

    @Column(name = "parent_company_id")
    private UUID parentCompanyId;

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
        return Company.builder()
            .companyName(companyName)
            .taxId(taxId)
            .companyType(companyType)
            .build();
    }

    public void setParent(UUID parentCompanyId) {
        this.parentCompanyId = parentCompanyId;
    }

    public boolean hasParent() {
        return this.parentCompanyId != null;
    }

    public void update(String companyName, String taxId, String address, String city, String country) {
        this.companyName = companyName;
        this.taxId = taxId;
        this.address = address;
        this.city = city;
        this.country = country;
    }

    @Override
    protected String getModuleCode() {
        return "COMP";
    }
}

