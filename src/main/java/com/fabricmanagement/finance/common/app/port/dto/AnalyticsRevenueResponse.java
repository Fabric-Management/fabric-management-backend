package com.fabricmanagement.finance.common.app.port.dto;

import com.fabricmanagement.finance.common.dto.FinanceWarningDto;
import java.util.List;
import lombok.Builder;

@Builder
public record AnalyticsRevenueResponse(
    List<AnalyticsRevenueRecordDto> records, List<FinanceWarningDto> warnings) {}
