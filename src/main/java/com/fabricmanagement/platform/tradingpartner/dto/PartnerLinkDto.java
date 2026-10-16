package com.fabricmanagement.platform.tradingpartner.dto;

import com.fabricmanagement.platform.tradingpartner.domain.PartnerLink;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerLinkRole;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** A company linked to a customer in a role, with the linked company's display facts. */
public record PartnerLinkDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID customerPartnerId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID linkedPartnerId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String linkedPartnerName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PartnerType linkedPartnerType,
    @Schema(nullable = true) String linkedPartnerCountry,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PartnerLinkRole role,
    @Schema(nullable = true) String note,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {

  public static PartnerLinkDto from(PartnerLink link) {
    TradingPartner linked = link.getLinkedPartner();
    return new PartnerLinkDto(
        link.getId(),
        link.getCustomerPartner().getId(),
        linked.getId(),
        linked.getDisplayName(),
        linked.getPartnerType(),
        linked.getCountry(),
        link.getRole(),
        link.getNote(),
        link.getCreatedAt());
  }
}
