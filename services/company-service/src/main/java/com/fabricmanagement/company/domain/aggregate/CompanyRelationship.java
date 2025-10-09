package com.fabricmanagement.company.domain.aggregate;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Company Relationship Entity
 * 
 * Represents trust-based business relationships between companies.
 * Used for cross-company data access control.
 * 
 * Example:
 * - Source: Manufacturer (us)
 * - Target: Customer Company X
 * - Type: CUSTOMER
 * - Status: ACTIVE
 * - Allowed Modules: ["orders", "shipments"]
 * 
 * Business Rules:
 * - Only ACTIVE relationships allow cross-company access
 * - Relationship must be unique per source-target pair
 * - Source and target cannot be the same company
 */
@Entity
@Table(name = "company_relationships")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CompanyRelationship extends BaseEntity {
    
    @Column(name = "source_company_id", nullable = false)
    private UUID sourceCompanyId;
    
    @Column(name = "target_company_id", nullable = false)
    private UUID targetCompanyId;
    
    @Column(name = "relationship_type", nullable = false, length = 50)
    private String relationshipType;  // CUSTOMER, SUPPLIER, SUBCONTRACTOR
    
    @Column(name = "status", nullable = false, length = 50)
    @lombok.Builder.Default
    private String status = "ACTIVE";  // ACTIVE, SUSPENDED, TERMINATED
    
    /**
     * Allowed modules for this relationship
     * Example: ["orders", "shipments", "invoices"]
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_modules", columnDefinition = "text[]")
    private List<String> allowedModules;
    
    /**
     * Allowed actions for this relationship
     * Example: ["READ", "WRITE"]
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_actions", columnDefinition = "text[]")
    private List<String> allowedActions;
    
    @Column(name = "start_date")
    private LocalDateTime startDate;
    
    @Column(name = "end_date")
    private LocalDateTime endDate;
    
    /**
     * Creates a new company relationship
     */
    public static CompanyRelationship create(UUID sourceCompanyId, UUID targetCompanyId,
                                            String relationshipType) {
        if (sourceCompanyId == null) {
            throw new IllegalArgumentException("Source company ID cannot be null");
        }
        if (targetCompanyId == null) {
            throw new IllegalArgumentException("Target company ID cannot be null");
        }
        if (sourceCompanyId.equals(targetCompanyId)) {
            throw new IllegalArgumentException("Source and target company cannot be the same");
        }
        if (relationshipType == null || relationshipType.trim().isEmpty()) {
            throw new IllegalArgumentException("Relationship type cannot be empty");
        }
        
        return CompanyRelationship.builder()
            .sourceCompanyId(sourceCompanyId)
            .targetCompanyId(targetCompanyId)
            .relationshipType(relationshipType.toUpperCase())
            .status("ACTIVE")
            .startDate(LocalDateTime.now())
            .build();
    }
    
    /**
     * Activates the relationship
     */
    public void activate() {
        this.status = "ACTIVE";
    }
    
    /**
     * Suspends the relationship temporarily
     */
    public void suspend() {
        this.status = "SUSPENDED";
    }
    
    /**
     * Terminates the relationship permanently
     */
    public void terminate() {
        this.status = "TERMINATED";
        this.endDate = LocalDateTime.now();
    }
    
    /**
     * Checks if relationship allows access
     */
    public boolean allowsAccess() {
        return "ACTIVE".equals(this.status) && 
               (this.endDate == null || this.endDate.isAfter(LocalDateTime.now()));
    }
    
    /**
     * Checks if a specific module is allowed
     */
    public boolean isModuleAllowed(String module) {
        return allowedModules != null && allowedModules.contains(module);
    }
    
    /**
     * Checks if a specific action is allowed
     */
    public boolean isActionAllowed(String action) {
        return allowedActions != null && allowedActions.contains(action);
    }
}
