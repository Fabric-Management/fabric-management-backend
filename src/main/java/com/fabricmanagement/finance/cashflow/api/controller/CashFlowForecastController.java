package com.fabricmanagement.finance.cashflow.api.controller;

import com.fabricmanagement.finance.cashflow.app.CashFlowForecastService;
import com.fabricmanagement.finance.cashflow.dto.CashFlowForecastDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/cash-flow/forecast")
@RequiredArgsConstructor
@Tag(name = "Finance - Cash Flow", description = "Cash flow forecasting operations")
public class CashFlowForecastController {

  private final CashFlowForecastService forecastService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(
      summary = "Generate cash flow forecast",
      description = "Generates a weekly cash flow forecast based on open AR and AP invoices.")
  public CashFlowForecastDto getForecast(
      @RequestParam(required = false) BigDecimal openingBalance,
      @RequestParam(required = false) Integer horizonWeeks) {
    return forecastService.generateForecast(openingBalance, horizonWeeks);
  }
}
