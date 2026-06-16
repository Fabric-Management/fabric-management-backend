package com.fabricmanagement.finance.fxexposure.dto;

import java.math.BigDecimal;

public record RealizedFxCurrencyDto(String currency, BigDecimal netRealizedGainLoss) {}
