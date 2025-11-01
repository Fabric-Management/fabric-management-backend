package com.fabricmanagement.common.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Base class for junction/association entities that use composite keys (@IdClass).
 *
 * <p>Unlike {@link BaseEntity}, this does NOT include an {@code id} field because
 * junction entities use composite primary keys defined via {@code @IdClass}.</p>
 *
 * <p>Provides common infrastructure fields for junction tables:</p>
 * <ul>
 *   <li><b>tenantId (UUID):</b> Multi-tenant isolation via Row-Level Security (RLS)</li>
 *   <li><b>uid (String):</b> Human-readable identifier for audit/debugging</li>
 *   <li><b>Audit fields:</b> createdAt, createdBy, updatedAt, updatedBy</li>
 *   <li><b>Soft delete:</b> isActive flag for logical deletion</li>
 *   <li><b>Optimistic locking:</b> version field for concurrent updates</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Entity
 * @IdClass(UserContactId.class)
 * public class UserContact extends BaseJunctionEntity {
 *
 *     @Id
 *     @Column(name = "user_id", nullable = false)
 *     private UUID userId;
 *
 *     @Id
 *     @Column(name = "contact_id", nullable = false)
 *     private UUID contactId;
 *
 *     // ... other fields
 * }
 * }</pre>
 *
 * <h2>Key Difference from BaseEntity:</h2>
 * <ul>
 *   <li>❌ NO {@code id} field (junction entities use composite keys)</li>
 *   <li>✅ All other BaseEntity features (tenantId, audit, soft delete, versioning)</li>
 * </ul>
 *
 * @see BaseEntity
 * @see TenantContext
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseJunctionEntity implements Serializable {

    /**
     * Tenant ID for multi-tenant isolation
     * <p>Automatically set from {@link TenantContext} on entity creation</p>
     * <p>Used for Row-Level Security (RLS) filtering in PostgreSQL</p>
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /**
     * Human-readable unique identifier
     * <p>Pattern: {TENANT_UID}-{MODULE}-{SEQUENCE}</p>
     * <p>Example: "ACME-001-UCON-00042", "XYZ-002-CADR-05123"</p>
     * <p>Used for audit logs, user interfaces, and support tickets</p>
     */
    @Column(name = "uid", unique = true, updatable = false, length = 100)
    private String uid;

    /**
     * Creation timestamp - automatically set on first persist
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Creator user ID - automatically set on first persist
     */
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    /**
     * Last modification timestamp - automatically updated on each update
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Last modifier user ID - automatically updated on each update
     */
    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    /**
     * Soft delete flag - true for active, false for deleted
     * <p>Used for logical deletion instead of physical deletion</p>
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Optimistic locking version field
     * <p>Prevents lost updates in concurrent transactions</p>
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /**
     * Pre-persist hook - sets tenant ID and timestamps.
     */
    @PrePersist
    protected void onCreate() {
        if (this.tenantId == null) {
            this.tenantId = TenantContext.getCurrentTenantId();
        }
        
        // Auto-generate UID if not set (simplified - can be enhanced with UIDGenerator)
        if (this.uid == null || this.uid.isBlank()) {
            String tenantUid = TenantContext.getCurrentTenantUid();
            if (tenantUid == null) {
                tenantUid = "SYS-000";
            }
            
            long sequence = System.currentTimeMillis() % 100000;
            this.uid = String.format("%s-%s-%05d", tenantUid, getModuleCode(), sequence);
        }
        
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    /**
     * Pre-update hook - updates modification timestamp
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Soft delete - marks entity as inactive
     */
    public void delete() {
        this.isActive = false;
    }

    /**
     * Reactivate - marks entity as active
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * Get module code for UID generation.
     * 
     * <p>Each junction entity must return its module code (e.g., "UCON", "CADR", "UADR")</p>
     * <p>This is used to generate human-readable UIDs.</p>
     *
     * @return module code (2-4 uppercase letters)
     */
    protected abstract String getModuleCode();
}

