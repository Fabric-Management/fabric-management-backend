package com.fabricmanagement.human.payroll.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.human.payroll.app.PayrollSelfServiceAppService;
import com.fabricmanagement.human.payroll.dto.SalarySlipDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/human/payroll/me")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payroll Self Service", description = "Payroll Self Service operations")
public class PayrollSelfServiceController {

  private final PayrollSelfServiceAppService selfServiceAppService;

  /**
   * Get current authenticated user's salary slips (payouts).
   *
   * <p>Used for the Self-Service portal (My Salary tab).
   */
  @PreAuthorize("@auth.can(authentication, 'settings', 'read')")
  @GetMapping("/salary-slips")
  public ResponseEntity<ApiResponse<List<SalarySlipDto>>> getMySalarySlips() {
    UUID userId = TenantContext.getCurrentUserId();

    if (userId == null) {
      throw new IllegalStateException("User not authenticated");
    }

    log.debug("Getting self-service salary slips for user ID: {}", userId);
    List<SalarySlipDto> slips = selfServiceAppService.getSalarySlipsForUser(userId);
    return ResponseEntity.ok(ApiResponse.success(slips));
  }
}
