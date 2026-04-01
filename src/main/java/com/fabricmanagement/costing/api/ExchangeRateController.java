package com.fabricmanagement.costing.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateSource;
import com.fabricmanagement.costing.dto.ExchangeRateResponse;
import com.fabricmanagement.costing.dto.ExchangeRateSubmitRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/costing/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

  private final ExchangeRateService exchangeRateService;

  /** Submit a rate — in the “ask when missing” flow, the frontend POSTs here. */
  @PostMapping
  @Operation(summary = "Submit exchange rate (manual entry or override)")
  @PreAuthorize("hasRole('COSTING_ADMIN')")
  public ApiResponse<Void> submitRate(@RequestBody @Valid ExchangeRateSubmitRequest request) {
    exchangeRateService.saveRate(
        request.baseCurrency(),
        request.targetCurrency(),
        request.rate(),
        request.rateDate() != null ? request.rateDate() : LocalDate.now(),
        ExchangeRateSource.MANUAL);
    return ApiResponse.success(null, "Exchange rate saved");
  }

  /**
   * Query the current rate — for frontend to display “is this rate correct?”.
   *
   * <p>Returns the rate if found, or a NOT_FOUND response with the queried pair so the frontend can
   * prompt the user to enter a manual rate.
   */
  @GetMapping
  @Operation(summary = "Get current exchange rate between two currencies")
  @PreAuthorize("hasRole('COSTING_READ')")
  public ResponseEntity<ApiResponse<ExchangeRateResponse>> getRate(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    LocalDate effectiveDate = date != null ? date : LocalDate.now();
    Optional<BigDecimal> rate = exchangeRateService.getRate(from, to, effectiveDate);

    if (rate.isPresent()) {
      return ResponseEntity.ok(
          ApiResponse.success(
              new ExchangeRateResponse(from, to, rate.get(), effectiveDate, "RETRIEVED")));
    } else {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              ApiResponse.success(
                  new ExchangeRateResponse(from, to, null, effectiveDate, "NOT_FOUND"),
                  "Exchange rate not found for " + from + " → " + to));
    }
  }
}
