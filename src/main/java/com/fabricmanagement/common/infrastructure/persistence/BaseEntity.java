package com.fabricmanagement.common.infrastructure.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base entity for all domain entities in the system.
 *
 * <p>Provides core infrastructure fields for:
 *
 * <ul>
 *   <li><b>id (UUID):</b> Machine-level primary key, globally unique
 *   <li><b>tenantId (UUID):</b> Multi-tenant isolation via Row-Level Security (RLS)
 *   <li><b>uid (String):</b> Human-readable identifier for audit/debugging (e.g.,
 *       "ACME-001-USER-00042")
 *   <li><b>Audit fields:</b> createdAt, createdBy, updatedAt, updatedBy
 *   <li><b>Soft delete:</b> isActive flag + deletedAt timestamp for logical deletion
 *   <li><b>Optimistic locking:</b> version field for concurrent updates
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * @Entity
 * @Table(name = "prod_product")
 * public class Product extends BaseEntity {
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
 *
 * <p>The {@code tenantId} field is automatically set from {@link TenantContext} during entity
 * creation. This ensures proper tenant isolation at the database level.
 *
 * <h2>Human-Readable IDs:</h2>
 *
 * <p>The {@code uid} field is auto-generated using {@link UIDGenerator} and follows the pattern:
 * {@code {TENANT_UID}-{MODULE}-{SEQUENCE}}
 *
 * <p>Example: "ACME-001-USER-00042", "XYZ-002-MAT-05123"
 *
 * @see TenantContext
 * @see UIDGenerator
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity implements Serializable, Persistable<UUID> {

  /** Primary key - UUID for global uniqueness across distributed systems */
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  /**
   * Tenant ID for multi-tenant isolation
   *
   * <p>Automatically set from {@link TenantContext} on entity creation
   *
   * <p>Used for Row-Level Security (RLS) filtering in PostgreSQL
   */
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  /**
   * Human-readable unique identifier
   *
   * <p>Pattern: {TENANT_UID}-{MODULE}-{ENTITY}-{SEQUENCE}
   *
   * <p>Example: "ACME-001-USER-00042", "XYZ-002-MAT-05123"
   *
   * <p>Used for audit logs, user interfaces, and support tickets
   */
  @Column(name = "uid", unique = true, updatable = false, length = 100)
  private String uid;

  /** Creation timestamp - automatically set on first persist */
  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Creator user ID - automatically set on first persist */
  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private UUID createdBy;

  /** Last modification timestamp - automatically updated on each update */
  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Last modifier user ID - automatically updated on each update */
  @LastModifiedBy
  @Column(name = "updated_by")
  private UUID updatedBy;

  /**
   * Soft delete flag - true for active, false for deleted
   *
   * <p>Used for logical deletion instead of physical deletion
   */
  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  /** Soft delete timestamp - records when the entity was logically deleted */
  @Column(name = "deleted_at")
  private Instant deletedAt;

  /**
   * Optimistic locking version field
   *
   * <p>Prevents lost updates in concurrent transactions
   */
  @Version
  @Column(name = "version", nullable = false)
  private Long version = 0L;

  /**
   * Get module code for UID generation.
   *
   * <p>Each entity must return its module code (e.g., "USER", "MAT", "INV")
   *
   * <p>This is used to generate human-readable UIDs.
   *
   * @return module code (2-4 uppercase letters)
   */
  protected abstract String getModuleCode();

  /**
   * Generate UID for this entity.
   *
   * <p><b>Template Method Pattern:</b> Subclasses can override this method to provide custom UID
   * generation logic.
   *
   * <p><b>Default Implementation:</b> Uses standard pattern {TENANT_UID}-{MODULE}-{UUID_SUFFIX}
   *
   * <p><b>Examples:</b>
   *
   * <ul>
   *   <li>User: "ACME-001-USER-A3F5B2C9"
   *   <li>Product: "ACME-001-MAT-B7E8F9A1"
   *   <li>Company (override): "ACME-001" (no module code)
   * </ul>
   *
   * @return Generated UID string
   */
  protected String generateUid() {
    String tenantUid = TenantContext.getCurrentTenantUid();
    if (tenantUid == null) {
      tenantUid = "SYS-000";
    }

    // Use UUID-based suffix to guarantee uniqueness
    // Format: {TENANT_UID}-{MODULE}-{UUID_SUFFIX}
    // Example: "ACME-001-USER-A3F5B2C9"
    String uniqueSuffix =
        UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

    return String.format("%s-%s-%s", tenantUid, getModuleCode(), uniqueSuffix);
  }

  /**
   * Pre-persist hook - sets tenant ID, UID, and timestamps.
   *
   * <p><b>UID Generation:</b> Uses generateUid() hook method, allowing subclasses to override for
   * custom UID generation.
   */
  @PrePersist
  protected void onCreate() {
    if (this.tenantId == null) {
      this.tenantId = TenantContext.getCurrentTenantId();
    }

    // Auto-generate UID if not set
    // ✅ Performance: Use UUID-based suffix for guaranteed uniqueness
    // This prevents collisions that can occur with timestamp-based sequences
    if (this.uid == null || this.uid.isBlank()) {
      this.uid = generateUid(); // Hook method - can be overridden by subclasses
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

  /** Pre-update hook - updates modification timestamp */
  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  /** Soft delete - marks entity as inactive and records deletion timestamp */
  public void delete() {
    this.isActive = false;
    this.deletedAt = Instant.now();
  }

  /** Reactivate - marks entity as active and clears deletion timestamp */
  public void activate() {
    this.isActive = true;
    this.deletedAt = null;
  }

  /** Check if entity is new (not persisted yet) */
  @Transient
  public boolean isNew() {
    return this.id == null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BaseEntity that)) {
      return false;
    }
    return id != null && id.equals(that.getId());
  }

  @Override
  public int hashCode() {
    if (id != null) {
      return id.hashCode();
    }
    return getClass().hashCode();
  }
}
