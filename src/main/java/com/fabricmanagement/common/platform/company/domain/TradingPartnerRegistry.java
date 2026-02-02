package com.fabricmanagement.common.platform.company.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * Platform-level golden record for trading partners.
 *
 * <p><b>CRITICAL:</b> This entity does NOT have tenant_id. It's a platform-wide registry that
 * enables cross-tenant partner matching via tax_id.
 *
 * <h2>Purpose:</h2>
 *
 * <p>When multiple tenants work with the same company (identified by tax_id + country), they all
 * reference the same registry record. This:
 *
 * <ul>
 *   <li>Eliminates data duplication across tenants
 *   <li>Enables cross-tenant partner linking
 *   <li>Provides a single source of truth for partner identity
 * </ul>
 *
 * <h2>Cross-Platform Linking:</h2>
 *
 * <p>When a partner becomes a platform tenant, {@code linkedTenantId} is set, enabling
 * bidirectional visibility. All tenants with this partner get notified via {@code
 * TradingPartnerLinkedEvent}.
 *
 * <h2>Tax ID Handling:</h2>
 *
 * <p>Tax ID is nullable for foreign or unregistered partners. When tax_id is NULL, each entry is
 * treated as unique (no deduplication possible). The UNIQUE constraint uses partial index for PG 14
 * compatibility.
 *
 * <h2>Example:</h2>
 *
 * <pre>{@code
 * // Tenant A adds "Supplier X" (tax: 123)
 * TradingPartnerRegistry registry = registryService.findOrCreate("123", "Supplier X", "TUR");
 *
 * // Tenant B adds same company (tax: 123)
 * TradingPartnerRegistry sameRegistry = registryService.findOrCreate("123", "Supplier X", "TUR");
 * // sameRegistry.id == registry.id (deduplicated)
 * }</pre>
 *
 * @see TradingPartner
 * @see VerifiedStatus
 */
@Entity
@Table(
    name = "trading_partner_registry",
    schema = "common_company",
    indexes = {
      @Index(name = "idx_tpr_linked_tenant", columnList = "linked_tenant_id"),
      @Index(name = "idx_tpr_name", columnList = "official_name"),
      @Index(name = "idx_tpr_verified", columnList = "verified_status")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingPartnerRegistry implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  /** Human-readable unique identifier (e.g., REG-12345678-001) */
  @Column(name = "uid", unique = true, nullable = false, length = 100)
  private String uid;

  /**
   * Tax identification number - nullable for foreign/unregistered partners.
   *
   * <p>When NOT NULL, combined with country forms unique constraint for deduplication.
   */
  @Column(name = "tax_id", length = 50)
  private String taxId;

  /** Official company name (source of truth) */
  @Column(name = "official_name", nullable = false, length = 255)
  private String officialName;

  /** ISO 3166-1 alpha-3 country code (e.g., TUR, USA, DEU) */
  @Column(name = "country", length = 3)
  private String country;

  @Enumerated(EnumType.STRING)
  @Column(name = "verified_status", nullable = false, length = 30)
  @Builder.Default
  private VerifiedStatus verifiedStatus = VerifiedStatus.UNVERIFIED;

  /**
   * If partner is a platform tenant, link to their tenant.
   *
   * <p>Enables cross-tenant visibility and bidirectional relationship management.
   */
  @Column(name = "linked_tenant_id")
  private UUID linkedTenantId;

  /** When verification was completed */
  @Column(name = "verification_date")
  private Instant verificationDate;

  /** User who performed verification */
  @Column(name = "verified_by")
  private UUID verifiedBy;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private Boolean isActive = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  @Builder.Default
  private Long version = 0L;

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
    this.updatedAt = Instant.now();
    if (this.isActive == null) {
      this.isActive = true;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  /**
   * Create a new registry entry.
   *
   * @param taxId Tax ID (nullable for foreign partners)
   * @param officialName Official company name
   * @param country ISO 3166-1 alpha-3 country code
   * @return new registry entry
   */
  public static TradingPartnerRegistry create(String taxId, String officialName, String country) {
    return TradingPartnerRegistry.builder()
        .taxId(taxId != null ? taxId.trim() : null)
        .officialName(officialName)
        .country(country)
        .verifiedStatus(VerifiedStatus.UNVERIFIED)
        .build();
  }

  /**
   * Link this registry to a platform tenant.
   *
   * <p>Called when a partner company becomes a platform tenant or when manually verified.
   *
   * @param tenantId The tenant ID to link
   * @param verifierUserId The user who performed the linking/verification
   */
  public void linkToTenant(UUID tenantId, UUID verifierUserId) {
    this.linkedTenantId = tenantId;
    this.verifiedStatus = VerifiedStatus.VERIFIED;
    this.verificationDate = Instant.now();
    this.verifiedBy = verifierUserId;
  }

  /** Check if this partner is linked to a platform tenant. */
  public boolean isLinkedToTenant() {
    return this.linkedTenantId != null;
  }

  /** Soft delete the registry entry. */
  public void delete() {
    this.isActive = false;
  }

  /** Reactivate the registry entry. */
  public void activate() {
    this.isActive = true;
  }
}
