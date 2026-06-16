package com.fabricmanagement.analytics.dto;

import java.util.List;

public record EstimatedMarginResponse(
    List<OrderMarginDto> orders, List<CustomerMarginDto> customers, String reportingCurrency) {}
