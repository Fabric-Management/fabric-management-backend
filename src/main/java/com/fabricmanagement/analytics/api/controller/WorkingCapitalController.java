package com.fabricmanagement.analytics.api.controller;

import com.fabricmanagement.analytics.app.WorkingCapitalService;
import com.fabricmanagement.analytics.dto.WorkingCapitalResponse;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
public class WorkingCapitalController {

  private final WorkingCapitalService workingCapitalService;

  @Operation(
      summary = "Get working capital (DSO + DPO + operating cash gap; DIO/full CCC pending INS-7b)")
  @GetMapping("/working-capital")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  public ResponseEntity<ApiResponse<WorkingCapitalResponse>> getWorkingCapital(
      @Parameter(description = "As-of date (defaults to today)", example = "2026-06-19")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOfDate) {
    return ResponseEntity.ok(
        ApiResponse.success(workingCapitalService.getWorkingCapital(asOfDate)));
  }
}
