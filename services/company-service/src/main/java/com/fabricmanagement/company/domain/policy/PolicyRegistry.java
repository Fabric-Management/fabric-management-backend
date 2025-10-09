package com.fabricmanagement.company.domain.policy;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import com.fabricmanagement.shared.domain.policy.DataScope;
import com.fabricmanagement.shared.domain.policy.OperationType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

/**
 * Policy Registry Entity
 * 
 * Endpoint security catalog
 */
@Entity
@Table(name = "policy_registry")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PolicyRegistry extends BaseEntity {
    
    @Column(name = "endpoint", nullable = false, unique = true, length = 200)
    private String endpoint;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 50)
    private OperationType operation;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 50)
    private DataScope scope;
    
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_company_types", columnDefinition = "text[]")
    private List<String> allowedCompanyTypes;
    
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "default_roles", columnDefinition = "text[]")
    private List<String> defaultRoles;
    
    @Column(name = "requires_grant", nullable = false)
    @lombok.Builder.Default
    private boolean requiresGrant = false;
    
    @Type(JsonBinaryType.class)
    @Column(name = "platform_policy", columnDefinition = "jsonb")
    private Map<String, Object> platformPolicy;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private boolean active = true;
    
    // Note: Column name is 'policy_version' to avoid conflict with BaseEntity.version (Long for optimistic locking)
    @Column(name = "policy_version", nullable = false, length = 20)
    @lombok.Builder.Default
    private String policyVersion = "v1";
    
    public void activate() {
        this.active = true;
    }
    
    public void deactivate() {
        this.active = false;
    }
    
    @PreUpdate
    protected void incrementVersion() {
        this.policyVersion = incrementVersionString(this.policyVersion);
    }
    
    private String incrementVersionString(String current) {
        if (current == null || !current.startsWith("v")) {
            return "v1";
        }
        
        String[] parts = current.substring(1).split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        
        return "v" + major + "." + (minor + 1);
    }
    
    public boolean isActive() {
        return active && !isDeleted();
    }
}

