package com.fabricmanagement.finance.period.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.finance.period.app.FinancialPeriodService;
import com.fabricmanagement.finance.period.dto.CloseFinancialPeriodResponse;
import com.fabricmanagement.finance.period.dto.EnsureFinancialPeriodRequest;
import com.fabricmanagement.finance.period.dto.FinancialPeriodDto;
import com.fabricmanagement.finance.period.dto.FxRevaluationDto;
import com.fabricmanagement.finance.period.dto.ReopenFinancialPeriodResponse;
import com.fabricmanagement.finance.period.dto.UnrealizedFxPositionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/periods")
@RequiredArgsConstructor
@Tag(name = "FinancialPeriod", description = "Financial period close and FX revaluation")
public class FinancialPeriodController {

  private final FinancialPeriodService financialPeriodService;

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'finance', 'manage')")
  @Operation(summary = "Ensure a monthly financial period exists")
  public ResponseEntity<ApiResponse<FinancialPeriodDto>> ensurePeriod(
      @Valid @RequestBody EnsureFinancialPeriodRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                financialPeriodService.ensurePeriod(request.year(), request.month())));
  }

  @GetMapping("/{periodId}")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(summary = "Get a financial period")
  public ResponseEntity<ApiResponse<FinancialPeriodDto>> getPeriod(@PathVariable UUID periodId) {
    return ResponseEntity.ok(ApiResponse.success(financialPeriodService.getPeriod(periodId)));
  }

  @PostMapping("/{periodId}/close")
  @PreAuthorize("@auth.can(authentication, 'finance', 'manage')")
  @Operation(summary = "Close a financial period and book unrealized FX revaluation")
  public ResponseEntity<ApiResponse<CloseFinancialPeriodResponse>> closePeriod(
      @PathVariable UUID periodId) {
    return ResponseEntity.ok(ApiResponse.success(financialPeriodService.closePeriod(periodId)));
  }

  @PostMapping("/{periodId}/reopen")
  @PreAuthorize("@auth.can(authentication, 'finance', 'manage')")
  @Operation(summary = "Reopen the latest closed financial period")
  public ResponseEntity<ApiResponse<ReopenFinancialPeriodResponse>> reopenPeriod(
      @PathVariable UUID periodId) {
    return ResponseEntity.ok(ApiResponse.success(financialPeriodService.reopenPeriod(periodId)));
  }

  @GetMapping("/latest-closed/unrealized-fx")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(summary = "Get unrealized FX position for the latest closed financial period")
  public ResponseEntity<ApiResponse<UnrealizedFxPositionDto>> getLatestClosedPosition() {
    return ResponseEntity.ok(ApiResponse.success(financialPeriodService.getLatestClosedPosition()));
  }

  @GetMapping("/{periodId}/unrealized-fx")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(summary = "Get unrealized FX position for a financial period")
  public ResponseEntity<ApiResponse<UnrealizedFxPositionDto>> getPosition(
      @PathVariable UUID periodId) {
    return ResponseEntity.ok(ApiResponse.success(financialPeriodService.getPosition(periodId)));
  }

  @GetMapping("/{periodId}/revaluations")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(summary = "List FX revaluation ledger entries for a financial period")
  public ResponseEntity<ApiResponse<List<FxRevaluationDto>>> getRevaluations(
      @PathVariable UUID periodId) {
    return ResponseEntity.ok(ApiResponse.success(financialPeriodService.getEntries(periodId)));
  }
}
