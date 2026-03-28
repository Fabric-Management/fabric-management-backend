package com.fabricmanagement.human.core.employee.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.human.core.employee.app.EmployeeService;
import com.fabricmanagement.human.core.employee.dto.EmployeeProfileDto;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/human/employees")
@RequiredArgsConstructor
@Slf4j
public class EmployeeProfileController {

  private final EmployeeService employeeService;

  /**
   * Get current authenticated user's HR (Employee) profile.
   *
   * <p>Used for the Self-Service portal (My HR Info tab).
   */
  @PreAuthorize("isAuthenticated()")
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<EmployeeProfileDto>> getMyHrProfile() {
    UUID userId = TenantContext.getCurrentUserId();

    if (userId == null) {
      throw new IllegalStateException("User not authenticated");
    }

    log.debug("Getting self-service HR profile for user ID: {}", userId);

    return employeeService
        .getEmployeeByUserId(userId)
        .map(EmployeeProfileDto::from)
        .map(dto -> ResponseEntity.ok(ApiResponse.success(dto)))
        .orElseGet(() -> ResponseEntity.ok(ApiResponse.success(null)));
  }
}
