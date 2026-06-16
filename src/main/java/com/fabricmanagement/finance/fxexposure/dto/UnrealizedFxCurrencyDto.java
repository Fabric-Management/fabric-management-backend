package com.fabricmanagement.finance.fxexposure.dto;

import java.math.BigDecimal;

public record UnrealizedFxCurrencyDto(String currency, BigDecimal netUnrealizedGainLoss) {}
