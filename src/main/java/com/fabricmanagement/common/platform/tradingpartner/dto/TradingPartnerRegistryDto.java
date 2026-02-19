package com.fabricmanagement.common.platform.tradingpartner.dto;

import com.fabricmanagement.common.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.common.platform.tradingpartner.domain.VerifiedStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * DTO for TradingPartnerRegistry (platform-level golden record).
 *
 * <p>Used for registry-level operations and platform admin views.
 */
@Data
@Builder
public class TradingPartnerRegistryDto {

  private UUID id;
  private String uid;

  private String taxId;
  private String officialName;
  private String country;

  private VerifiedStatus verifiedStatus;
  private UUID linkedTenantId;
  private Instant verificationDate;

  private Boolean isActive;
  private Instant createdAt;

  /**
   * Create DTO from TradingPartnerRegistry entity.
   *
   * @param registry TradingPartnerRegistry entity
   * @return DTO
   */
  public static TradingPartnerRegistryDto from(TradingPartnerRegistry registry) {
    return TradingPartnerRegistryDto.builder()
        .id(registry.getId())
        .uid(registry.getUid())
        .taxId(registry.getTaxId())
        .officialName(registry.getOfficialName())
        .country(registry.getCountry())
        .verifiedStatus(registry.getVerifiedStatus())
        .linkedTenantId(registry.getLinkedTenantId())
        .verificationDate(registry.getVerificationDate())
        .isActive(registry.getIsActive())
        .createdAt(registry.getCreatedAt())
        .build();
  }
}
