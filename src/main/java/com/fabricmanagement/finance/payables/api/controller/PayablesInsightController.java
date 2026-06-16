package com.fabricmanagement.finance.payables.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.finance.payables.app.PayablesInsightService;
import com.fabricmanagement.finance.payables.dto.PayablesSummaryDto;
import com.fabricmanagement.finance.payables.dto.PayablesSupplierDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/payables")
@RequiredArgsConstructor
@Tag(name = "Payables Insights", description = "Read-only accounts payable intelligence")
public class PayablesInsightController {

  private final PayablesInsightService payablesInsightService;

  @GetMapping("/summary")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(summary = "Get payables summary and risk metrics")
  public ResponseEntity<ApiResponse<PayablesSummaryDto>> getSummary(
      @RequestParam(defaultValue = "5") Integer topN) {
    return ResponseEntity.ok(ApiResponse.success(payablesInsightService.getSummary(topN)));
  }

  @GetMapping("/suppliers")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(summary = "List per-supplier payables rollups")
  public ResponseEntity<ApiResponse<PagedResponse<PayablesSupplierDto>>> getSuppliers(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(payablesInsightService.getSuppliers(pageable))));
  }
}
