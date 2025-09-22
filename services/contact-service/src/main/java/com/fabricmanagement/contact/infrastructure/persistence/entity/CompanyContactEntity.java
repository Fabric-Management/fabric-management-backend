package com.fabricmanagement.contact.infrastructure.persistence.entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * Contact entity specifically for companies.
 * Links contact information to a company in the company-service.
 * Extends ContactEntity which in turn extends BaseEntity for common functionality.
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
@SuperBuilder
public class CompanyContactEntity extends ContactEntity {

    @NotNull(message = "Company ID is required")
    @Column(name = "company_id", nullable = false, unique = true)
    private UUID companyId;

    @NotBlank(message = "Company name is required")
    @Size(max = 200, message = "Company name cannot exceed 200 characters")
    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Pattern(regexp = "^https?://.*", message = "Website must be a valid URL")
    @Size(max = 500, message = "Website URL cannot exceed 500 characters")
    @Column(name = "website", length = 500)
    private String website;

    @Size(max = 200, message = "Main contact person name cannot exceed 200 characters")
    @Column(name = "main_contact_person", length = 200)
    private String mainContactPerson;

    @Email(message = "Main contact email must be valid")
    @Size(max = 100, message = "Main contact email cannot exceed 100 characters")
    @Column(name = "main_contact_email", length = 100)
    private String mainContactEmail;

    @Pattern(regexp = "^[+]?[0-9\\s\\-().]{3,50}$", message = "Main contact phone must be a valid phone number")
    @Size(max = 50, message = "Main contact phone cannot exceed 50 characters")
    @Column(name = "main_contact_phone", length = 50)
    private String mainContactPhone;

    @Size(max = 200, message = "Business hours cannot exceed 200 characters")
    @Column(name = "business_hours", length = 200)
    private String businessHours;

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
    public boolean belongsToCompany(UUID companyId) {
        return this.companyId != null && this.companyId.equals(companyId);
    }
}