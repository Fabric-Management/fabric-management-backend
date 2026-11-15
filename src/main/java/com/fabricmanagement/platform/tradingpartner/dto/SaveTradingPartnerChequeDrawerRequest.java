package com.fabricmanagement.platform.tradingpartner.dto;

import com.fabricmanagement.platform.tradingpartner.domain.ChequeDrawerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Adds a cheque drawer, or renames / retypes one (PAYMENT-PLAN-7 R15, R18). */
@Schema(name = "SaveTradingPartnerChequeDrawerRequest")
public record SaveTradingPartnerChequeDrawerRequest(
    @NotBlank @Size(max = 200) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
        String name,
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ChequeDrawerType drawerType) {}
