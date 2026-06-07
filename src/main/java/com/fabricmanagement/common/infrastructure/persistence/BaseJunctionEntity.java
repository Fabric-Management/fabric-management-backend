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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base class for junction/association entities that use composite keys (@IdClass).
 *
 * <p>Unlike {@link BaseEntity}, this does NOT include an {@code id} field because junction entities
 * use composite primary keys defined via {@code @IdClass}.
 *
 * <p>Provides common infrastructure fields for junction tables:
 *
 * <ul>
 *   <li><b>tenantId (UUID):</b> Multi-tenant isolation via Row-Level Security (RLS)
 *   <li><b>uid (String):</b> Human-readable identifier for audit/debugging
 *   <li><b>Audit fields:</b> createdAt, createdBy, updatedAt, updatedBy
 *   <li><b>Soft delete:</b> isActive flag + deletedAt timestamp for logical deletion
 *   <li><b>Optimistic locking:</b> version field for concurrent updates
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
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
 *
 * <ul>
 *   <li>❌ NO {@code id} field (junction entities use composite keys)
 *   <li>✅ All other BaseEntity features (tenantId, audit, soft delete, versioning)
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
   * <p>Pattern: {TENANT_UID}-{MODULE}-{SEQUENCE}
   *
   * <p>Example: "ACME-001-UCON-00042", "XYZ-002-CADR-05123"
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

  /** Soft delete timestamp - records when the junction was logically deleted */
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

  /** Pre-persist hook - sets tenant ID and timestamps. */
  @PrePersist
  protected void onCreate() {
    if (this.tenantId == null) {
      this.tenantId = TenantContext.requireTenantId();
    }

    // Auto-generate UID if not set (simplified - can be enhanced with UIDGenerator)
    if (this.uid == null || this.uid.isBlank()) {
      String tenantUid = TenantContext.getCurrentTenantUid();
      if (tenantUid == null) {
        tenantUid = "SYS-000";
      }

      String uniqueSuffix =
          UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
      this.uid = String.format("%s-%s-%s", tenantUid, getModuleCode(), uniqueSuffix);
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

  /** Soft delete - marks junction as inactive and records deletion timestamp */
  public void delete() {
    this.isActive = false;
    this.deletedAt = Instant.now();
  }

  /** Reactivate - marks junction as active and clears deletion timestamp */
  public void activate() {
    this.isActive = true;
    this.deletedAt = null;
  }

  /**
   * Get module code for UID generation.
   *
   * <p>Each junction entity must return its module code (e.g., "UCON", "CADR", "UADR")
   *
   * <p>This is used to generate human-readable UIDs.
   *
   * @return module code (2-4 uppercase letters)
   */
  protected abstract String getModuleCode();
}
