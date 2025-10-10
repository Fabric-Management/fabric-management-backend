package com.fabricmanagement.company.domain.aggregate;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import com.fabricmanagement.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.company.domain.event.CompanyUpdatedEvent;
import com.fabricmanagement.company.domain.event.CompanyDeletedEvent;
import com.fabricmanagement.company.domain.valueobject.CompanyName;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.company.domain.valueobject.CompanyType;
import com.fabricmanagement.company.domain.valueobject.Industry;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Company Aggregate Root
 * 
 * Represents a company in the system with multi-tenancy support.
 * Follows Domain-Driven Design principles with event sourcing.
 */
@Entity
@Table(name = "companies")
@Getter
@NoArgsConstructor
@SuperBuilder
public class Company extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false, length = 100))
    private CompanyName name;
    
    @Column(name = "legal_name", length = 200)
    private String legalName;
    
    @Column(name = "tax_id", length = 50)
    private String taxId;
    
    @Column(name = "registration_number", length = 100)
    private String registrationNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private CompanyType type;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "industry", nullable = false, length = 30)
    private Industry industry;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CompanyStatus status;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "website", length = 255)
    private String website;
    
    @Column(name = "logo_url", length = 500)
    private String logoUrl;
    
    @Type(JsonBinaryType.class)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;
    
    @Type(JsonBinaryType.class)
    @Column(name = "preferences", columnDefinition = "jsonb")
    private Map<String, Object> preferences;
    
    @Column(name = "subscription_start_date")
    private LocalDateTime subscriptionStartDate;
    
    @Column(name = "subscription_end_date")
    private LocalDateTime subscriptionEndDate;
    
    @Column(name = "subscription_plan", length = 50)
    private String subscriptionPlan;
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive;
    
    @Column(name = "max_users", nullable = false)
    private int maxUsers;
    
    @Column(name = "current_users", nullable = false)
    private int currentUsers;
    
    // =========================================================================
    // POLICY AUTHORIZATION FIELDS (V2.0)
    // =========================================================================
    
    /**
     * Business type - relationship to system
     * MANUFACTURER: Main production company (us)
     * CUSTOMER: Customer company
     * SUPPLIER: Supplier company
     * SUBCONTRACTOR: Subcontractor company
     * 
     * Note: Different from 'type' field (legal entity type: CORPORATION, LLC, etc.)
     *       'businessType' is about business relationship
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 50)
    @lombok.Builder.Default
    private com.fabricmanagement.shared.domain.policy.CompanyType businessType 
        = com.fabricmanagement.shared.domain.policy.CompanyType.INTERNAL;
    
    /**
     * Parent company ID
     * NULL for MANUFACTURER (we are the parent)
     * Set for external companies (CUSTOMER/SUPPLIER/SUBCONTRACTOR) - links to us
     */
    @Column(name = "parent_company_id")
    private UUID parentCompanyId;
    
    /**
     * Relationship type to parent company
     * NULL for MANUFACTURER
     * CUSTOMER/SUPPLIER/SUBCONTRACTOR for external companies
     */
    @Column(name = "relationship_type", length = 50)
    private String relationshipType;
    
    // Domain events (transient - not persisted)
    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    /**
     * Creates a new company with business validation
     */
    public static Company create(UUID tenantId, CompanyName name, String legalName,
                               CompanyType type, Industry industry, String description) {
        
        // Business validation
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("Company name cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Company type cannot be null");
        }
        if (industry == null) {
            throw new IllegalArgumentException("Industry cannot be null");
        }

        Company company = Company.builder()
            .tenantId(tenantId)
            .name(name)
            .legalName(legalName)
            .type(type)
            .industry(industry)
            .description(description)
            .status(CompanyStatus.ACTIVE)
            .isActive(true)
            .maxUsers(10) // Default max users
            .currentUsers(0)
            .subscriptionPlan("BASIC")
            .subscriptionStartDate(LocalDateTime.now())
            .subscriptionEndDate(LocalDateTime.now().plusYears(1))
            .build();

        // Add domain event
        company.addDomainEvent(new CompanyCreatedEvent(
            company.getId(),
            company.getTenantId(),
            company.getName().getValue(),
            company.getType().toString(),
            company.getIndustry().toString()
        ));

        return company;
    }

    /**
     * Updates company information
     */
    public void updateCompany(String legalName, String description, String website) {
        this.legalName = legalName;
        this.description = description;
        this.website = website;

        // Add domain event
        addDomainEvent(new CompanyUpdatedEvent(
            this.getId(),
            this.getTenantId(),
            this.getName().getValue(),
            this.getType().toString()
        ));
    }
    
    /**
     * Sets registration details (tax ID and registration number)
     * Used during company creation for optional registration fields
     */
    public void setRegistrationDetails(String taxId, String registrationNumber) {
        this.taxId = taxId;
        this.registrationNumber = registrationNumber;
    }
    
    /**
     * Configures policy authorization fields
     * Sets business type, parent company relationship
     * 
     * @param businessType INTERNAL, CUSTOMER, SUPPLIER, SUBCONTRACTOR
     * @param parentCompanyId Parent company UUID (for external companies)
     * @param relationshipType Business relationship type
     */
    public void configurePolicyFields(
            com.fabricmanagement.shared.domain.policy.CompanyType businessType,
            UUID parentCompanyId,
            String relationshipType) {
        this.businessType = businessType;
        this.parentCompanyId = parentCompanyId;
        this.relationshipType = relationshipType;
    }

    /**
     * Updates company settings
     */
    public void updateSettings(Map<String, Object> settings) {
        this.settings = settings;
        
        addDomainEvent(new CompanyUpdatedEvent(
            this.getId(),
            this.getTenantId(),
            this.getName().getValue(),
            this.getType().toString()
        ));
    }

    /**
     * Updates company preferences
     */
    public void updatePreferences(Map<String, Object> preferences) {
        this.preferences = preferences;
        
        addDomainEvent(new CompanyUpdatedEvent(
            this.getId(),
            this.getTenantId(),
            this.getName().getValue(),
            this.getType().toString()
        ));
    }

    /**
     * Updates subscription plan
     */
    public void updateSubscription(String plan, int maxUsers, LocalDateTime endDate) {
        this.subscriptionPlan = plan;
        this.maxUsers = maxUsers;
        this.subscriptionEndDate = endDate;
        
        addDomainEvent(new CompanyUpdatedEvent(
            this.getId(),
            this.getTenantId(),
            this.getName().getValue(),
            this.getType().toString()
        ));
    }

    /**
     * Activates the company
     */
    public void activate() {
        this.status = CompanyStatus.ACTIVE;
        this.isActive = true;
        
        addDomainEvent(new CompanyUpdatedEvent(
            this.getId(),
            this.getTenantId(),
            this.getName().getValue(),
            this.getType().toString()
        ));
    }

    /**
     * Deactivates the company
     */
    public void deactivate() {
        this.status = CompanyStatus.INACTIVE;
        this.isActive = false;
        
        addDomainEvent(new CompanyUpdatedEvent(
            this.getId(),
            this.getTenantId(),
            this.getName().getValue(),
            this.getType().toString()
        ));
    }

    /**
     * Suspends the company
     */
    public void suspend() {
        this.status = CompanyStatus.SUSPENDED;
        this.isActive = false;
        
        addDomainEvent(new CompanyUpdatedEvent(
            this.getId(),
            this.getTenantId(),
            this.getName().getValue(),
            this.getType().toString()
        ));
    }

    /**
     * Adds a user to the company
     */
    public void addUser() {
        if (currentUsers >= maxUsers) {
            throw new IllegalArgumentException("Maximum user limit reached");
        }
        
        this.currentUsers++;
        
        addDomainEvent(new CompanyUpdatedEvent(
            this.getId(),
            this.getTenantId(),
            this.getName().getValue(),
            this.getType().toString()
        ));
    }

    /**
     * Removes a user from the company
     */
    public void removeUser() {
        if (currentUsers <= 0) {
            throw new IllegalArgumentException("No users to remove");
        }
        
        this.currentUsers--;
        
        addDomainEvent(new CompanyUpdatedEvent(
            this.getId(),
            this.getTenantId(),
            this.getName().getValue(),
            this.getType().toString()
        ));
    }

    /**
     * Updates company logo
     */
    public void updateLogo(String logoUrl) {
        this.logoUrl = logoUrl;
        
        addDomainEvent(new CompanyUpdatedEvent(
            this.getId(),
            this.getTenantId(),
            this.getName().getValue(),
            this.getType().toString()
        ));
    }

    /**
     * Soft deletes the company
     */
    @Override
    public void markAsDeleted() {
        super.markAsDeleted();
        this.status = CompanyStatus.DELETED;
        this.isActive = false;
        
        addDomainEvent(new CompanyDeletedEvent(
            this.getId(),
            this.getTenantId(),
            this.getName().getValue(),
            this.getType().toString()
        ));
    }

    /**
     * Checks if company is active
     */
    public boolean isActive() {
        return CompanyStatus.ACTIVE.equals(this.status) && isActive && !isDeleted();
    }

    /**
     * Checks if company can add more users
     */
    public boolean canAddUser() {
        return currentUsers < maxUsers && isActive();
    }

    /**
     * Checks if subscription is active
     */
    public boolean isSubscriptionActive() {
        return subscriptionEndDate != null && subscriptionEndDate.isAfter(LocalDateTime.now());
    }

    /**
     * Gets company display name
     */
    public String getDisplayName() {
        return name.getValue();
    }

    /**
     * Adds domain event
     */
    private void addDomainEvent(Object event) {
        this.domainEvents.add(event);
    }

    /**
     * Gets and clears domain events
     */
    public List<Object> getAndClearDomainEvents() {
        List<Object> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }
}
