package com.fabricmanagement.platform.tradingpartner.dto;

import com.fabricmanagement.platform.tradingpartner.domain.PartnerStatus;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * DTO for TradingPartner responses.
 *
 * <p>Combines data from TradingPartner (tenant-level) and TradingPartnerRegistry (platform-level)
 * for a complete view of the partner relationship.
 */
@Data
@Builder
public class TradingPartnerDto {

  // Identifiers
  private UUID id;
  private String uid;
  private UUID registryId;

  // Display info (from registry + custom override)
  private String displayName;
  private String officialName;
  private String customName;
  private String taxId;
  private String country;

  // Relationship
  private PartnerType partnerType;
  private PartnerStatus status;

  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  private Map<String, Object> relationshipMeta;

  // Platform link
  private boolean onPlatform;
  private UUID linkedTenantId;
  private String verifiedStatus;

  // Partner Organization
  private UUID organizationId;
  private String preferredCurrency;
  private boolean pendingAccountingReview;

  @Schema(nullable = true)
  private UUID acquiredById;

  // Audit
  private Instant createdAt;
  private Boolean isActive;

  // Migration traceability (for transition period)
  private UUID legacyCompanyId;

  /**
   * Create DTO from TradingPartner entity.
   *
   * @param tp TradingPartner entity with loaded registry
   * @return DTO
   */
  public static TradingPartnerDto from(TradingPartner tp) {
    TradingPartnerRegistry registry = tp.getRegistry();

    return TradingPartnerDto.builder()
        .id(tp.getId())
        .uid(tp.getUid())
        .registryId(registry != null ? registry.getId() : null)
        .displayName(tp.getDisplayName())
        .officialName(registry != null ? registry.getOfficialName() : null)
        .customName(tp.getCustomName())
        .taxId(registry != null ? registry.getTaxId() : null)
        .country(registry != null ? registry.getCountry() : null)
        .partnerType(tp.getPartnerType())
        .status(tp.getStatus())
        .relationshipMeta(tp.getRelationshipMeta())
        .onPlatform(tp.isOnPlatform())
        .linkedTenantId(tp.getLinkedTenantId())
        .verifiedStatus(registry != null ? registry.getVerifiedStatus().name() : null)
        .organizationId(tp.getOrganizationId())
        .pendingAccountingReview(tp.isPendingAccountingReview())
        .acquiredById(tp.getAcquiredById())
        .createdAt(tp.getCreatedAt())
        .isActive(tp.getIsActive())
        .legacyCompanyId(tp.getLegacyCompanyId())
        .build();
  }
}
