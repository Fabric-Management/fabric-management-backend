package com.fabricmanagement.company.domain.aggregate;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import com.fabricmanagement.company.domain.valueobject.CompanyName;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.company.domain.valueobject.CompanyType;
import com.fabricmanagement.company.domain.valueobject.Industry;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Company Aggregate Root
 * 
 * Anemic Domain Model - Pure data holder
 * Business logic → Service layer
 * Mapping logic → Mapper layer
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
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
    
    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 50)
    @lombok.Builder.Default
    private com.fabricmanagement.shared.domain.policy.CompanyType businessType 
        = com.fabricmanagement.shared.domain.policy.CompanyType.INTERNAL;
    
    @Column(name = "parent_company_id")
    private UUID parentCompanyId;
    
    @Column(name = "relationship_type", length = 50)
    private String relationshipType;
    
    @Column(name = "is_platform", nullable = false)
    @lombok.Builder.Default
    private boolean isPlatform = false;
    
    @Column(name = "address_line1", length = 255)
    private String addressLine1;
    
    @Column(name = "address_line2", length = 255)
    private String addressLine2;
    
    @Column(name = "city", length = 100)
    private String city;
    
    @Column(name = "district", length = 100)
    private String district;
    
    @Column(name = "postal_code", length = 20)
    private String postalCode;
    
    @Column(name = "country", length = 100, nullable = false)
    @lombok.Builder.Default
    private String country = "Turkey";
}
