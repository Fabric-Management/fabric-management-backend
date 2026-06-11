package com.fabricmanagement.platform.tradingpartner.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.offline.domain.OfflineMetadata;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Type;

/**
 * Tenant-specific trading partner relationship.
 *
 * <p>Links a tenant to a {@link TradingPartnerRegistry} record with relationship-specific data
 * (partner type, custom name, payment terms, etc.).
 *
 * <h2>Architecture:</h2>
 *
 * <ul>
 *   <li>Registry holds "golden record" (official name, tax ID, verification status)
 *   <li>TradingPartner holds relationship data (type, custom name, terms)
 *   <li>Same registry can have multiple TradingPartner records (one per tenant)
 * </ul>
 *
 * <h2>Deduplication:</h2>
 *
 * <p>The UNIQUE constraint (tenant_id, registry_id) ensures one relationship per tenant-registry
 * pair. When the same partner is added with different types, use {@link #upgradeToMultiType} to
 * change to {@link PartnerType#BOTH}.
 *
 * <h2>Legacy Migration:</h2>
 *
 * <p>{@code legacyCompanyId} preserves the link to migrated Company records for FK traceability
 * during transition period. This enables dual-read queries that find partners by either new ID or
 * legacy Company ID.
 *
 * <h2>Example:</h2>
 *
 * <pre>{@code
 * // Create new partner relationship
 * TradingPartner partner = TradingPartner.create(registry, PartnerType.SUPPLIER, "Our Main Supplier");
 * partner.getRelationshipMeta().put("payment_terms", "NET30");
 * partner.getRelationshipMeta().put("credit_limit", 100000);
 *
 * // Check if partner is on platform
 * if (partner.isOnPlatform()) {
 *     UUID partnerTenantId = partner.getLinkedTenantId();
 *     // Enable cross-tenant features
 * }
 * }</pre>
 *
 * @see TradingPartnerRegistry
 * @see PartnerType
 * @see PartnerStatus
 */
@Entity
@Table(
    name = "common_trading_partner",
    schema = "common_company",
    indexes = {
      @Index(name = "idx_tp_tenant", columnList = "tenant_id"),
      @Index(name = "idx_tp_registry", columnList = "registry_id"),
      @Index(name = "idx_tp_type", columnList = "partner_type"),
      @Index(name = "idx_tp_status", columnList = "status"),
      @Index(name = "idx_tp_organization", columnList = "organization_id"),
      @Index(name = "idx_tp_legacy", columnList = "legacy_company_id")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_tp_tenant_registry",
          columnNames = {"tenant_id", "registry_id"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingPartner extends BaseEntity {

  /**
   * Reference to platform-level golden record.
   *
   * <p>Multiple tenants can reference the same registry, enabling cross-tenant deduplication.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "registry_id", nullable = false)
  private TradingPartnerRegistry registry;

  /**
   * Tenant's custom name/alias for this partner (optional).
   *
   * <p>Allows tenants to use their own naming (e.g., "Our Primary Yarn Supplier") while registry
   * maintains official name.
   */
  @Column(name = "custom_name", length = 255)
  private String customName;

  @Enumerated(EnumType.STRING)
  @Column(name = "partner_type", nullable = false, length = 30)
  private PartnerType partnerType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  @Builder.Default
  private PartnerStatus status = PartnerStatus.ACTIVE;

  /**
   * Relationship-specific metadata.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>payment_terms: "NET30", "NET60"
   *   <li>credit_limit: 100000
   *   <li>discount_rate: 5.0
   *   <li>contact_person: "John Doe"
   *   <li>contact_email: "john@partner.com"
   *   <li>notes: "Preferred supplier for cotton yarn"
   * </ul>
   */
  @Type(JsonType.class)
  @Column(name = "relationship_meta", columnDefinition = "jsonb")
  @Builder.Default
  private Map<String, Object> relationshipMeta = new HashMap<>();

  /**
   * Linked Organization ID for user management.
   *
   * <p>When a TradingPartner is created, a corresponding "partner Organization" record is
   * auto-created with type EXTERNAL_PARTNER. External users for this partner are linked to this
   * Organization, enabling reuse of existing user management flows.
   *
   * @see com.fabricmanagement.platform.organization.domain.OrganizationType#EXTERNAL_PARTNER
   */
  @Column(name = "organization_id")
  private UUID organizationId;

  /**
   * Legacy Company ID for migration traceability.
   *
   * <p>Preserves link to old Company record during transition. Enables dual-read queries that find
   * partners by either new TradingPartner.id or legacy Company.id.
   *
   * <p>Will be nullable after full migration is complete.
   */
  @Column(name = "legacy_company_id")
  private UUID legacyCompanyId;

  @Embedded private OfflineMetadata offlineMetadata;

  @Override
  protected String getModuleCode() {
    return "TP";
  }

  /**
   * Create a new trading partner relationship.
   *
   * @param registry The platform-level registry record
   * @param partnerType The type of relationship
   * @param customName Optional custom name/alias
   * @return new trading partner
   */
  public static TradingPartner create(
      TradingPartnerRegistry registry, PartnerType partnerType, String customName) {
    return TradingPartner.builder()
        .registry(registry)
        .partnerType(partnerType)
        .customName(customName)
        .status(PartnerStatus.ACTIVE)
        .relationshipMeta(new HashMap<>())
        .build();
  }

  /**
   * Get display name for UI.
   *
   * <p>Returns custom name if set, otherwise falls back to registry official name.
   *
   * @return display name
   */
  public String getDisplayName() {
    if (customName != null && !customName.isBlank()) {
      return customName;
    }
    return registry != null ? registry.getOfficialName() : null;
  }

  /**
   * Upgrade partner type when adding a new relationship type.
   *
   * <p>Handles all valid type combinations:
   *
   * <ul>
   *   <li>SUPPLIER + CUSTOMER (or vice versa) → BOTH
   *   <li>SUBCONTRACTOR + SUPPLIER/CUSTOMER → BOTH (fason partners can also supply/buy)
   *   <li>SERVICE_PROVIDER + any → BOTH
   *   <li>Same type → no change
   *   <li>Already BOTH → no change
   * </ul>
   *
   * @param newType The additional relationship type
   * @return true if upgrade occurred, false if no change needed
   */
  public boolean upgradeToMultiType(PartnerType newType) {
    if (this.partnerType == newType || this.partnerType == PartnerType.BOTH) {
      return false;
    }
    if (newType == PartnerType.BOTH) {
      this.partnerType = PartnerType.BOTH;
      return true;
    }
    // Any combination of different types results in BOTH
    this.partnerType = PartnerType.BOTH;
    return true;
  }

  /**
   * Check if this partner is on the platform (has a linked tenant).
   *
   * @return true if partner is a platform tenant
   */
  public boolean isOnPlatform() {
    return registry != null && registry.isLinkedToTenant();
  }

  /**
   * Get the linked tenant ID if partner is on platform.
   *
   * @return linked tenant ID or null
   */
  public UUID getLinkedTenantId() {
    return registry != null ? registry.getLinkedTenantId() : null;
  }

  /**
   * Get tax ID from registry.
   *
   * @return tax ID or null
   */
  public String getTaxId() {
    return registry != null ? registry.getTaxId() : null;
  }

  /**
   * Get official name from registry.
   *
   * @return official name or null
   */
  public String getOfficialName() {
    return registry != null ? registry.getOfficialName() : null;
  }

  /**
   * Get country from registry.
   *
   * @return country code or null
   */
  public String getCountry() {
    return registry != null ? registry.getCountry() : null;
  }

  /** Suspend the partner relationship. */
  public void suspend() {
    this.status = PartnerStatus.SUSPENDED;
  }

  /** Block the partner relationship. */
  public void block() {
    this.status = PartnerStatus.BLOCKED;
  }

  /** Reactivate a suspended or pending partner. */
  public void reactivate() {
    if (this.status.canActivate()) {
      this.status = PartnerStatus.ACTIVE;
    }
  }
}
