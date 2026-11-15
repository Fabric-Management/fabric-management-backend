package com.fabricmanagement.platform.tradingpartner.dto;

import com.fabricmanagement.platform.tradingpartner.domain.ChequeDrawerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerChequeDrawer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** One of a customer's cheque drawers (PAYMENT-PLAN-7 R13). */
@Schema(name = "TradingPartnerChequeDrawerDto")
public record TradingPartnerChequeDrawerDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID partnerId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ChequeDrawerType drawerType,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "False once deactivated: kept for history, not offered in the picker")
        boolean active) {

  public static TradingPartnerChequeDrawerDto from(TradingPartnerChequeDrawer drawer) {
    return new TradingPartnerChequeDrawerDto(
        drawer.getId(),
        drawer.getPartnerId(),
        drawer.getName(),
        drawer.getDrawerType(),
        drawer.isUsable());
  }
}
