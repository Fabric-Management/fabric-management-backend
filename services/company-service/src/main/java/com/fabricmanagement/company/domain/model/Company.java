package com.fabricmanagement.company.domain.model;

import com.fabricmanagement.common.core.domain.base.BaseEntity;
import com.fabricmanagement.company.domain.valueobject.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Company domain entity representing company business data in the system.
 * Extends BaseEntity for common functionality (id, auditing, soft delete).
 * Focused ONLY on company business data - delegates contact info to contact-service.
 *
 * Service Integration:
 * - References contact info via UUID (handled by contact-service)
 * - References employee info via UUID Set (handled by user-service)
 * - NO direct database access to other services
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class Company extends BaseEntity {

    private UUID tenantId;

    // Core company business information
    private String companyName;
    private String displayName;
    private String description;
    private String registrationNumber;
    private String taxNumber;
    private LocalDate foundedDate;

    // Company classification
    private Industry industry;
    private CompanyType companyType;
    private CompanySize companySize;
    private CompanyStatus status;

    // Financial information
    private BigDecimal annualRevenue;
    private Currency currency;
    private String fiscalYearEnd;

    // Business relationships and certifications
    private String website;
    @Builder.Default
    private Set<String> certifications = new HashSet<>();
    @Builder.Default
    private Set<String> businessLicenses = new HashSet<>();

    // Cross-service references (UUID only - no direct access)
    private UUID primaryContactId; // Links to contact-service
    @Builder.Default
    private Set<UUID> employeeIds = new HashSet<>(); // Links to user-service
    @Builder.Default
    private Set<UUID> subsidiaryIds = new HashSet<>(); // Links to other companies
    private UUID parentCompanyId; // Links to parent company if subsidiary

    // Business logic methods
    public String getFullCompanyName() {
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        return companyName != null ? companyName : "";
    }

    public void activate() {
        this.status = CompanyStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = CompanyStatus.INACTIVE;
    }

    public void suspend() {
        this.status = CompanyStatus.SUSPENDED;
    }

    public void blacklist() {
        this.status = CompanyStatus.BLACKLISTED;
    }

    public boolean isActive() {
        return CompanyStatus.ACTIVE.equals(status);
    }

    public boolean isSubsidiary() {
        return parentCompanyId != null;
    }

    public boolean hasSubsidiaries() {
        return !subsidiaryIds.isEmpty();
    }

    public void addEmployee(UUID employeeId) {
        if (employeeId != null) {
            this.employeeIds.add(employeeId);
        }
    }

    public void removeEmployee(UUID employeeId) {
        this.employeeIds.remove(employeeId);
    }

    public void addSubsidiary(UUID subsidiaryId) {
        if (subsidiaryId != null && !subsidiaryId.equals(getId())) {
            this.subsidiaryIds.add(subsidiaryId);
        }
    }

    public void removeSubsidiary(UUID subsidiaryId) {
        this.subsidiaryIds.remove(subsidiaryId);
    }

    public void addCertification(String certification) {
        if (certification != null && !certification.trim().isEmpty()) {
            this.certifications.add(certification.trim());
        }
    }

    public void removeCertification(String certification) {
        this.certifications.remove(certification);
    }

    public void addBusinessLicense(String license) {
        if (license != null && !license.trim().isEmpty()) {
            this.businessLicenses.add(license.trim());
        }
    }

    public void removeBusinessLicense(String license) {
        this.businessLicenses.remove(license);
    }

    public void updateBusinessInfo(String companyName, String displayName, String description,
                                  Industry industry, CompanyType companyType, CompanySize companySize) {
        this.companyName = companyName;
        this.displayName = displayName;
        this.description = description;
        this.industry = industry;
        this.companyType = companyType;
        this.companySize = companySize;
    }

    public void updateFinancialInfo(BigDecimal annualRevenue, Currency currency, String fiscalYearEnd) {
        this.annualRevenue = annualRevenue;
        this.currency = currency;
        this.fiscalYearEnd = fiscalYearEnd;
    }

    public boolean hasFinancialData() {
        return annualRevenue != null && currency != null;
    }

    public boolean isEstablishedCompany() {
        return foundedDate != null && foundedDate.isBefore(LocalDate.now().minusYears(1));
    }

    @Override
    public String toString() {
        return "Company{" +
            "id=" + getId() +
            ", tenantId=" + tenantId +
            ", companyName='" + companyName + '\'' +
            ", industry=" + industry +
            ", status=" + status +
            ", deleted=" + isDeleted() +
            '}';
    }
}