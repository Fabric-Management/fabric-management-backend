package com.fabricmanagement.contact.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * CompanyContact domain entity representing contact information for a company.
 * Focuses only on contact-related information, not company business data.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class CompanyContact extends Contact {

    // Company identification (reference to company-service)
    private UUID companyId;
    private String companyName;

    // Contact-specific information (what contact-service manages)
    private String website;
    private String mainContactPerson;
    private String mainContactEmail;
    private String mainContactPhone;
    private String businessHours;

    // Default constructor
    public CompanyContact() {
        super();
        this.setContactType("COMPANY");
    }

    // Constructor for creation
    public CompanyContact(UUID companyId, String companyName, UUID tenantId) {
        super();
        this.companyId = companyId;
        this.companyName = companyName;
        this.setTenantId(tenantId);
        this.setDisplayName(companyName);
        this.setContactType("COMPANY");
    }

    // Factory method
    public static CompanyContact createForCompany(UUID companyId, UUID tenantId, String companyName) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID cannot be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        if (companyName == null || companyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Company name cannot be null or empty");
        }

        return new CompanyContact(companyId, companyName, tenantId);
    }

    // Business logic specific to contact management
    public boolean belongsToCompany(UUID companyId) {
        return this.companyId != null && this.companyId.equals(companyId);
    }

    public boolean hasMainContact() {
        return mainContactPerson != null && !mainContactPerson.trim().isEmpty();
    }

    public boolean hasContactEmail() {
        return mainContactEmail != null && !mainContactEmail.trim().isEmpty();
    }

    public boolean hasContactPhone() {
        return mainContactPhone != null && !mainContactPhone.trim().isEmpty();
    }

    public void updateMainContact(String person, String email, String phone) {
        this.mainContactPerson = person;
        this.mainContactEmail = email;
        this.mainContactPhone = phone;
    }

    public void updateWebsite(String website) {
        if (website != null && !website.trim().isEmpty() && !website.matches("^https?://.*")) {
            throw new IllegalArgumentException("Website must be a valid URL starting with http:// or https://");
        }
        this.website = website;
    }

    public void updateBusinessHours(String businessHours) {
        this.businessHours = businessHours;
    }
}