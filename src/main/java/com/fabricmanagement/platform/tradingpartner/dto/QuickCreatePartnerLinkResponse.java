package com.fabricmanagement.platform.tradingpartner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Outcome of a quick-create-and-link call.
 *
 * <p>{@code created} is true when the link was written; then {@code partner} and {@code link} are
 * set and {@code matches} is empty. When similar tenant partners exist and the caller did not
 * confirm, {@code created} is false, nothing was written, and {@code matches} lists the candidates
 * (link one of them, or repeat the call with {@code createAnyway}).
 */
public record QuickCreatePartnerLinkResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean created,
    @Schema(nullable = true) TradingPartnerDto partner,
    @Schema(nullable = true) PartnerLinkDto link,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PartnerMatchDto> matches) {

  public static QuickCreatePartnerLinkResponse linked(
      TradingPartnerDto partner, PartnerLinkDto link) {
    return new QuickCreatePartnerLinkResponse(true, partner, link, List.of());
  }

  public static QuickCreatePartnerLinkResponse needsDecision(List<PartnerMatchDto> matches) {
    return new QuickCreatePartnerLinkResponse(false, null, null, List.copyOf(matches));
  }
}
