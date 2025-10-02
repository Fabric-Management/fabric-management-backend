package com.fabricmanagement.company.domain.valueobject;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Company User Entity
 * 
 * Represents the many-to-many relationship between companies and users.
 * Manages user roles and access within a company.
 */
@Entity
@Table(name = "company_users")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CompanyUser extends BaseEntity {
    
    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "role", nullable = false, length = 50)
    private String role;
    
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive;
    
    /**
     * Creates a new company user relationship
     */
    public static CompanyUser create(UUID companyId, UUID userId, String role) {
        return CompanyUser.builder()
            .companyId(companyId)
            .userId(userId)
            .role(role)
            .joinedAt(LocalDateTime.now())
            .isActive(true)
            .build();
    }
    
    /**
     * Deactivates the user from the company
     */
    public void deactivate() {
        this.isActive = false;
    }
    
    /**
     * Activates the user in the company
     */
    public void activate() {
        this.isActive = true;
    }
    
    /**
     * Updates the user's role
     */
    public void updateRole(String newRole) {
        this.role = newRole;
    }
}

