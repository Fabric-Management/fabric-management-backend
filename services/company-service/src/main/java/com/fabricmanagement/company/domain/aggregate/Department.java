package com.fabricmanagement.company.domain.aggregate;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import com.fabricmanagement.shared.domain.policy.DepartmentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Department Aggregate Root
 * 
 * Represents a functional department within a company.
 * Only applicable to INTERNAL companies.
 * 
 * Purpose:
 * - Organizational structure
 * - Department-based dashboard routing
 * - Permission scoping
 * 
 * Examples:
 * - Code: WEAVING, Type: PRODUCTION
 * - Code: ACCOUNTING, Type: FINANCE
 * - Code: QC, Type: QUALITY
 * 
 * Business Rules:
 * - Department code must be unique within company
 * - Only INTERNAL companies can have departments
 * - One manager per department
 */
@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Department extends BaseEntity {
    
    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    
    @Column(name = "code", nullable = false, length = 50)
    private String code;
    
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    
    @Column(name = "name_en", length = 200)
    private String nameEn;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private DepartmentType type;
    
    @Column(name = "manager_id")
    private UUID managerId;
    
    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private boolean active = true;
    
    /**
     * Creates a new department with validation
     */
    public static Department create(UUID companyId, String code, String name, 
                                    DepartmentType type) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID cannot be null");
        }
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Department code cannot be empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("Department type cannot be null");
        }
        
        return Department.builder()
            .companyId(companyId)
            .code(code.toUpperCase())
            .name(name)
            .type(type)
            .active(true)
            .build();
    }
    
    /**
     * Activates the department
     */
    public void activate() {
        this.active = true;
    }
    
    /**
     * Deactivates the department
     */
    public void deactivate() {
        this.active = false;
    }
    
    /**
     * Assigns a manager to the department
     */
    public void assignManager(UUID managerId) {
        if (managerId == null) {
            throw new IllegalArgumentException("Manager ID cannot be null");
        }
        this.managerId = managerId;
    }
    
    /**
     * Removes manager assignment
     */
    public void removeManager() {
        this.managerId = null;
    }
    
    /**
     * Checks if department is active
     */
    public boolean isActive() {
        return active && !isDeleted();
    }
    
    /**
     * Gets display name (localized or default)
     */
    public String getDisplayName(String locale) {
        if ("en".equalsIgnoreCase(locale) && nameEn != null) {
            return nameEn;
        }
        return name;
    }
}

