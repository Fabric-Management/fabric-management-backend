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
 * Base entity for all domain entities in the system.
 *
 * <p>Provides core infrastructure fields for:
 * <ul>
 *   <li><b>id (UUID):</b> Machine-level primary key, globally unique</li>
 *   <li><b>tenantId (UUID):</b> Multi-tenant isolation via Row-Level Security (RLS)</li>
 *   <li><b>uid (String):</b> Human-readable identifier for audit/debugging (e.g., "ACME-001-USER-00042")</li>
 *   <li><b>Audit fields:</b> createdAt, createdBy, updatedAt, updatedBy</li>
 *   <li><b>Soft delete:</b> isActive flag for logical deletion</li>
 *   <li><b>Optimistic locking:</b> version field for concurrent updates</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Entity
 * @Table(name = "prod_material")
 * public class Material extends BaseEntity {
 *     @Column(nullable = false)
 *     private String name;
 *
 *     @Override
 *     protected String getModuleCode() {
 *         return "MAT";  // Module code for UID generation
 *     }
 * }
 * }</pre>
 *
 * <h2>Multi-Tenancy:</h2>
 * <p>The {@code tenantId} field is automatically set from {@link TenantContext}
 * during entity creation. This ensures proper tenant isolation at the database level.</p>
 *
 * <h2>Human-Readable IDs:</h2>
 * <p>The {@code uid} field is auto-generated using {@link UIDGenerator} and follows
 * the pattern: {@code {TENANT_UID}-{MODULE}-{SEQUENCE}}</p>
 * <p>Example: "ACME-001-USER-00042", "XYZ-002-MAT-05123"</p>
 *
 * @see TenantContext
 * @see UIDGenerator
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity implements Serializable {

    /**
     * Primary key - UUID for global uniqueness across distributed systems
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Tenant ID for multi-tenant isolation
     * <p>Automatically set from {@link TenantContext} on entity creation</p>
     * <p>Used for Row-Level Security (RLS) filtering in PostgreSQL</p>
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /**
     * Human-readable unique identifier
     * <p>Pattern: {TENANT_UID}-{MODULE}-{ENTITY}-{SEQUENCE}</p>
     * <p>Example: "ACME-001-USER-00042", "XYZ-002-MAT-05123"</p>
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
     * Get module code for UID generation.
     * 
     * <p>Each entity must return its module code (e.g., "USER", "MAT", "INV")</p>
     * <p>This is used to generate human-readable UIDs.</p>
     *
     * @return module code (2-4 uppercase letters)
     */
    protected abstract String getModuleCode();

    /**
     * Pre-persist hook - sets tenant ID, UID, and timestamps.
     */
    @PrePersist
    protected void onCreate() {
        if (this.tenantId == null) {
            this.tenantId = TenantContext.getCurrentTenantId();
        }
        
        // Auto-generate UID if not set
        if (this.uid == null || this.uid.isBlank()) {
            String tenantUid = TenantContext.getCurrentTenantUid();
            if (tenantUid == null) {
                tenantUid = "SYS-000";
            }
            
            // Simple sequence based on timestamp (TODO: use DB sequence)
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
     * Check if entity is new (not persisted yet)
     */
    @Transient
    public boolean isNew() {
        return this.id == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity that)) return false;
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

