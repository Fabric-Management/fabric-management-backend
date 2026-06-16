package com.fabricmanagement.analytics.api.controller;

import com.fabricmanagement.analytics.app.EstimatedMarginService;
import com.fabricmanagement.analytics.dto.EstimatedMarginResponse;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Cross-context analytics APIs")
public class EstimatedMarginController {

  private final EstimatedMarginService estimatedMarginService;

  @Operation(summary = "Get estimated margins for active orders")
  @GetMapping("/margin/estimated")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  public ResponseEntity<ApiResponse<EstimatedMarginResponse>> getEstimatedMargin() {
    return ResponseEntity.ok(ApiResponse.success(estimatedMarginService.getEstimatedMargin()));
  }
}
