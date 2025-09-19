package com.fabricmanagement.contact.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Contact entity specifically for companies.
 * Links contact information to a company in the company-service.
 */
@Entity
@Table(name = "company_contacts", indexes = {
    @Index(name = "idx_company_contact_company_id", columnList = "company_id", unique = true),
    @Index(name = "idx_company_contact_tenant_company", columnList = "tenant_id, company_id")
})
@DiscriminatorValue("COMPANY")
@PrimaryKeyJoinColumn(name = "contact_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CompanyContactEntity extends ContactEntity {

    @Column(name = "company_id", nullable = false, unique = true)
    private UUID companyId;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "company_size", length = 50)
    private String companySize;

    @Column(name = "website", length = 500)
    private String website;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(name = "registration_number", length = 50)
    private String registrationNumber;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "annual_revenue")
    private Long annualRevenue;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "position", length = 100)
    private String position;

    @Column(name = "business_unit", length = 100)
    private String businessUnit;

    @Column(name = "main_contact_person", length = 200)
    private String mainContactPerson;

    @Column(name = "main_contact_email", length = 100)
    private String mainContactEmail;

    @Column(name = "main_contact_phone", length = 50)
    private String mainContactPhone;

    @Column(name = "business_hours", length = 200)
    private String businessHours;

    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Column(name = "credit_limit")
    private Long creditLimit;

    /**
     * Creates a new CompanyContactEntity with the given company ID and tenant ID.
     */
    public static CompanyContactEntity createForCompany(UUID companyId, UUID tenantId, String companyName) {
        CompanyContactEntity entity = new CompanyContactEntity();
        entity.setCompanyId(companyId);
        entity.setTenantId(tenantId);
        entity.setCompanyName(companyName);
        return entity;
    }

    /**
     * Checks if this contact belongs to the specified company.
     */
    @Transient
    public boolean belongsToCompany(UUID companyId) {
        return this.companyId != null && this.companyId.equals(companyId);
    }
}