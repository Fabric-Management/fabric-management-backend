package com.fabricmanagement.common.platform.tenant.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Tenant entity - Platform-level subscription and settings.
 *
 * <p><b>CRITICAL:</b> This is a PLATFORM-LEVEL entity. Unlike other entities that use {@code
 * tenant_id} for isolation, Tenant IS the isolation boundary itself.
 *
 * <h2>Architecture:</h2>
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  PLATFORM LEVEL (no tenant_id - global)                         │
 * │  ├── Tenant (this entity)                                       │
 * │  └── TradingPartnerRegistry                                     │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  TENANT LEVEL (tenant_id scoped)                                │
 * │  ├── Organization (internal structure)                          │
 * │  ├── TradingPartner (external partners)                         │
 * │  ├── User, Employee, Department                                 │
 * │  └── All business entities                                      │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Responsibilities:</h2>
 *
 * <ul>
 *   <li>Subscription management (status, trial dates)
 *   <li>Platform-wide settings (timezone, locale, branding)
 *   <li>Billing anchor (Stripe customer ID, etc.)
 *   <li>Data isolation boundary for all tenant-scoped entities
 * </ul>
 *
 * <h2>Example:</h2>
 *
 * <pre>{@code
 * Tenant tenant = Tenant.create("ACME Corporation", "ACME-001");
 * tenant.getStatus(); // TRIAL
 * tenant.getSettings().getTimezone(); // "UTC"
 * }</pre>
 *
 * @see TenantStatus
 * @see TenantSettings
 */
@Entity
@Table(
    name = "common_tenant",
    schema = "common_tenant",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_tenant_uid",
          columnNames = {"uid"}),
      @UniqueConstraint(
          name = "uk_tenant_slug",
          columnNames = {"slug"})
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant implements Serializable {

  private static final long serialVersionUID = 1L;

  // ========================================
  // IDENTITY
  // ========================================

  /** Primary key - UUID for global uniqueness */
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  /**
   * Human-readable unique identifier.
   *
   * <p>Format: {PREFIX}-{SEQUENCE}, e.g., "ACME-001", "AKKAYALAR-002"
   *
   * <p>Used for:
   *
   * <ul>
   *   <li>UID generation in child entities (e.g., "ACME-001-USER-A3F5B2C9")
   *   <li>Support tickets and audit logs
   *   <li>URL slugs (optional)
   * </ul>
   */
  @Column(name = "uid", unique = true, nullable = false, length = 50)
  private String uid;

  /**
   * URL-friendly slug for tenant identification.
   *
   * <p>Format: lowercase, hyphenated, e.g., "acme-corp", "akkayalar-tekstil"
   *
   * <p>Used for subdomain routing: {slug}.fabricmanagement.com
   */
  @Column(name = "slug", unique = true, nullable = false, length = 100)
  private String slug;

  // ========================================
  // BASIC INFO
  // ========================================

  /** Display name of the tenant/organization */
  @Column(name = "name", nullable = false, length = 255)
  private String name;

  /** Primary email for billing and critical notifications */
  @Column(name = "billing_email", length = 255)
  private String billingEmail;

  // ========================================
  // SUBSCRIPTION STATUS
  // ========================================

  /** Current lifecycle status */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private TenantStatus status = TenantStatus.TRIAL;

  /** Trial period end date. Null if not in trial or unlimited trial. */
  @Column(name = "trial_ends_at")
  private Instant trialEndsAt;

  /** Subscription plan identifier (e.g., "professional", "enterprise") */
  @Column(name = "subscription_plan", length = 50)
  private String subscriptionPlan;

  // ========================================
  // SETTINGS (JSONB)
  // ========================================

  /** Tenant-wide settings stored as JSONB */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "settings", columnDefinition = "jsonb")
  @Builder.Default
  private TenantSettings settings = TenantSettings.defaults();

  // ========================================
  // EXTERNAL INTEGRATIONS
  // ========================================

  /** Stripe customer ID for billing integration */
  @Column(name = "stripe_customer_id", length = 100)
  private String stripeCustomerId;

  // ========================================
  // AUDIT FIELDS
  // ========================================

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private UUID createdBy;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @LastModifiedBy
  @Column(name = "updated_by")
  private UUID updatedBy;

  /** Soft delete flag */
  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private Boolean isActive = true;

  /** Optimistic locking version */
  @Version
  @Column(name = "version", nullable = false)
  @Builder.Default
  private Long version = 0L;

  // ========================================
  // FACTORY METHODS
  // ========================================

  /**
   * Create a new tenant with default trial status.
   *
   * @param name Display name
   * @param uid Human-readable UID (e.g., "ACME-001")
   * @return new Tenant in TRIAL status
   */
  public static Tenant create(String name, String uid) {
    String slug = generateSlug(name);
    return Tenant.builder()
        .name(name)
        .uid(uid)
        .slug(slug)
        .status(TenantStatus.TRIAL)
        .settings(TenantSettings.defaults())
        .build();
  }

  /**
   * Create a new tenant with custom settings.
   *
   * @param name Display name
   * @param uid Human-readable UID
   * @param settings Custom settings
   * @return new Tenant in TRIAL status
   */
  public static Tenant create(String name, String uid, TenantSettings settings) {
    String slug = generateSlug(name);
    return Tenant.builder()
        .name(name)
        .uid(uid)
        .slug(slug)
        .status(TenantStatus.TRIAL)
        .settings(settings != null ? settings : TenantSettings.defaults())
        .build();
  }

  // ========================================
  // BUSINESS METHODS
  // ========================================

  /**
   * Activate tenant after payment or trial conversion.
   *
   * @param plan Subscription plan name
   */
  public void activate(String plan) {
    this.status = TenantStatus.ACTIVE;
    this.subscriptionPlan = plan;
    this.trialEndsAt = null;
  }

  /** Suspend tenant due to payment failure or policy violation. */
  public void suspend() {
    if (this.status == TenantStatus.CANCELLED) {
      throw new IllegalStateException("Cannot suspend a cancelled tenant");
    }
    this.status = TenantStatus.SUSPENDED;
  }

  /** Cancel tenant subscription permanently. */
  public void cancel() {
    this.status = TenantStatus.CANCELLED;
    this.isActive = false;
  }

  /**
   * Start trial period.
   *
   * @param trialDays Number of trial days
   */
  public void startTrial(int trialDays) {
    this.status = TenantStatus.TRIAL;
    this.trialEndsAt = Instant.now().plusSeconds(trialDays * 24L * 60 * 60);
  }

  /**
   * Check if tenant has platform access.
   *
   * @return true if tenant can use the platform
   */
  public boolean hasAccess() {
    return this.isActive && this.status.hasAccess();
  }

  /**
   * Check if trial has expired.
   *
   * @return true if trial period has ended
   */
  public boolean isTrialExpired() {
    return this.status == TenantStatus.TRIAL
        && this.trialEndsAt != null
        && Instant.now().isAfter(this.trialEndsAt);
  }

  // ========================================
  // UTILITY METHODS
  // ========================================

  /**
   * Generate URL-friendly slug from name.
   *
   * @param name Company/tenant name
   * @return lowercase hyphenated slug
   */
  private static String generateSlug(String name) {
    if (name == null || name.isBlank()) {
      return "tenant-" + UUID.randomUUID().toString().substring(0, 8);
    }
    return name.toLowerCase()
        .trim()
        .replaceAll("[^a-z0-9\\s-]", "")
        .replaceAll("\\s+", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }

  /** Soft delete */
  public void delete() {
    this.isActive = false;
  }

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
    if (this.updatedAt == null) {
      this.updatedAt = Instant.now();
    }
    if (this.isActive == null) {
      this.isActive = true;
    }
    if (this.settings == null) {
      this.settings = TenantSettings.defaults();
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
