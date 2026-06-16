package com.fabricmanagement.finance.receivables.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.finance.receivables.app.ReceivablesInsightService;
import com.fabricmanagement.finance.receivables.dto.ReceivablesCustomerDto;
import com.fabricmanagement.finance.receivables.dto.ReceivablesSummaryDto;
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
@RequestMapping("/api/v1/finance/receivables")
@RequiredArgsConstructor
@Tag(name = "Receivables Insights", description = "Read-only accounts receivable intelligence")
public class ReceivablesInsightController {

  private final ReceivablesInsightService receivablesInsightService;

  @GetMapping("/summary")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(summary = "Get receivables summary and risk metrics")
  public ResponseEntity<ApiResponse<ReceivablesSummaryDto>> getSummary(
      @RequestParam(defaultValue = "5") Integer topN) {
    return ResponseEntity.ok(ApiResponse.success(receivablesInsightService.getSummary(topN)));
  }

  @GetMapping("/customers")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(summary = "List per-customer receivables rollups")
  public ResponseEntity<ApiResponse<PagedResponse<ReceivablesCustomerDto>>> getCustomers(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(receivablesInsightService.getCustomers(pageable))));
  }
}
