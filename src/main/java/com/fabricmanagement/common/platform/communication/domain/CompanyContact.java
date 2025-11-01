package com.fabricmanagement.common.platform.communication.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.common.platform.company.domain.Company;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * CompanyContact junction entity - Links Company to Contact.
 *
 * <p>Represents the relationship between a Company and their Contact information.
 * Supports multiple contacts per company (main phone, fax, email, website, etc.).</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>✅ Multiple contacts per company</li>
 *   <li>✅ Default contact for business communication</li>
 *   <li>✅ Department-specific contacts</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Link company to main phone
 * CompanyContact companyContact = CompanyContact.builder()
 *     .company(company)
 *     .contact(mainPhoneContact)
 *     .isDefault(true)
 *     .department(null)  // Main phone, not department-specific
 *     .build();
 *
 * // Link company to sales department email
 * CompanyContact salesEmail = CompanyContact.builder()
 *     .company(company)
 *     .contact(salesEmailContact)
 *     .isDefault(false)
 *     .department("Sales")
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "common_company_contact", schema = "common_communication",
    indexes = {
        @Index(name = "idx_company_contact_company", columnList = "company_id"),
        @Index(name = "idx_company_contact_contact", columnList = "contact_id"),
        @Index(name = "idx_company_contact_tenant", columnList = "tenant_id"),
        @Index(name = "idx_company_contact_department", columnList = "department")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(CompanyContactId.class)
public class CompanyContact extends BaseJunctionEntity {

    @Id
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Id
    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", insertable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", insertable = false, updatable = false)
    private Contact contact;

    /**
     * Default contact for business communication
     * <p>true = use this contact as default for company-wide communications</p>
     */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    /**
     * Department-specific contact
     * <p>If set, this contact is for a specific department (e.g., "Sales", "Support")</p>
     * <p>null = company-wide contact</p>
     */
    @Column(name = "department", length = 100)
    private String department;

    /**
     * Set as default contact
     */
    public void setAsDefault() {
        this.isDefault = true;
    }

    /**
     * Check if this is department-specific
     */
    public boolean isDepartmentSpecific() {
        return department != null && !department.isBlank();
    }

    @Override
    protected String getModuleCode() {
        return "CCON";
    }
}
