package com.fabricmanagement.human.payroll.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.human.core.employee.application.EmployeeService;
import com.fabricmanagement.human.core.employee.domain.Employee;
import com.fabricmanagement.human.payroll.api.dto.SalarySlipDto;
import com.fabricmanagement.human.payroll.infra.repository.PayRunPayoutRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/human/payroll/me")
@RequiredArgsConstructor
@Slf4j
public class PayrollSelfServiceController {

  private final EmployeeService employeeService;
  private final PayRunPayoutRepository payRunPayoutRepository;

  /**
   * Get current authenticated user's salary slips (payouts).
   *
   * <p>Used for the Self-Service portal (My Salary tab).
   */
  @PreAuthorize("isAuthenticated()")
  @GetMapping("/salary-slips")
  public ResponseEntity<ApiResponse<List<SalarySlipDto>>> getMySalarySlips() {
    UUID userId = TenantContext.getCurrentUserId();

    if (userId == null) {
      throw new IllegalStateException("User not authenticated");
    }

    log.debug("Getting self-service salary slips for user ID: {}", userId);

    Optional<Employee> employeeOpt = employeeService.getEmployeeByUserId(userId);
    if (employeeOpt.isEmpty()) {
      return ResponseEntity.ok(ApiResponse.success(List.of()));
    }

    Employee employee = employeeOpt.get();

    List<SalarySlipDto> slips =
        payRunPayoutRepository.findDescByEmployeeId(employee.getId()).stream()
            .map(SalarySlipDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(slips));
  }
}
