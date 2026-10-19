package com.fabricmanagement.platform.tradingpartner.dto;

import com.fabricmanagement.platform.tradingpartner.domain.PartnerMatchReason;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** An existing tenant partner that looks like the company being entered. */
public record PartnerMatchDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID partnerId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PartnerType partnerType,
    @Schema(nullable = true) String country,
    @Schema(nullable = true) String taxId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PartnerMatchReason matchedOn) {

  public static PartnerMatchDto of(TradingPartner partner, PartnerMatchReason matchedOn) {
    return new PartnerMatchDto(
        partner.getId(),
        partner.getDisplayName(),
        partner.getPartnerType(),
        partner.getCountry(),
        partner.getTaxId(),
        matchedOn);
  }
}
