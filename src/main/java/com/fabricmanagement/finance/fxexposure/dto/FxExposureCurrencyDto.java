package com.fabricmanagement.finance.fxexposure.dto;

import java.math.BigDecimal;

public record FxExposureCurrencyDto(
    String currency,
    BigDecimal grossAccountsReceivable,
    BigDecimal grossAccountsPayable,
    BigDecimal netDocumentCurrency,
    BigDecimal netReportingCurrency) {}
