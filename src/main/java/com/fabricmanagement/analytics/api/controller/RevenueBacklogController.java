package com.fabricmanagement.analytics.api.controller;

import com.fabricmanagement.analytics.app.RevenueBacklogService;
import com.fabricmanagement.analytics.dto.RevenueBacklogResponse;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Cross-context analytics APIs")
public class RevenueBacklogController {

  private final RevenueBacklogService revenueBacklogService;

  @Operation(summary = "Get revenue and backlog trends by customer")
  @GetMapping("/trends")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  public ResponseEntity<ApiResponse<RevenueBacklogResponse>> getRevenueBacklogTrends(
      @Parameter(description = "Number of trailing months to analyze", example = "12")
          @RequestParam(required = false, defaultValue = "12")
          int months) {
    // Basic validation
    int validMonths = Math.max(1, Math.min(24, months));
    return ResponseEntity.ok(ApiResponse.success(revenueBacklogService.getTrends(validMonths)));
  }
}
