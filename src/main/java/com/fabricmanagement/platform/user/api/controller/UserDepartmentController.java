package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.user.api.facade.UserDepartmentFacade;
import com.fabricmanagement.platform.user.dto.AssignDepartmentRequest;
import com.fabricmanagement.platform.user.dto.UserDepartmentDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/users/{userId}/departments")
@RequiredArgsConstructor
@Slf4j
public class UserDepartmentController {

  private final UserDepartmentFacade userDepartmentFacade;

  /**
   * Get all departments assigned to a user.
   *
   * <p>TenantContext is automatically set by JwtContextInterceptor.
   */
  @GetMapping
  public ResponseEntity<ApiResponse<List<UserDepartmentDto>>> getUserDepartments(
      @PathVariable UUID userId) {
    log.debug("Getting user departments: userId={}", userId);

    List<UserDepartmentDto> assignments = userDepartmentFacade.getUserDepartments(userId);

    return ResponseEntity.ok(ApiResponse.success(assignments));
  }

  @GetMapping("/primary")
  public ResponseEntity<ApiResponse<UserDepartmentDto>> getPrimaryDepartment(
      @PathVariable UUID userId) {
    log.debug("Getting primary department: userId={}", userId);

    UserDepartmentDto primary =
        userDepartmentFacade
            .getPrimaryDepartment(userId)
            .orElseThrow(() -> new IllegalArgumentException("No primary department found"));

    return ResponseEntity.ok(ApiResponse.success(primary));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<UserDepartmentDto>> assignDepartment(
      @PathVariable UUID userId, @Valid @RequestBody AssignDepartmentRequest request) {
    log.info(
        "Assigning department to user: userId={}, departmentId={}, isPrimary={}",
        userId,
        request.getDepartmentId(),
        request.getIsPrimary());

    UserDepartmentDto assignment =
        userDepartmentFacade.assignDepartment(
            userId,
            request.getDepartmentId(),
            request.getIsPrimary() != null && request.getIsPrimary(),
            TenantContext.getCurrentUserId());

    return ResponseEntity.ok(ApiResponse.success(assignment, "Department assigned successfully"));
  }

  @PutMapping("/{departmentId}/primary")
  public ResponseEntity<ApiResponse<UserDepartmentDto>> setPrimaryDepartment(
      @PathVariable UUID userId, @PathVariable UUID departmentId) {
    log.info("Setting primary department: userId={}, departmentId={}", userId, departmentId);

    userDepartmentFacade.setPrimaryDepartment(userId, departmentId);

    UserDepartmentDto primary =
        userDepartmentFacade
            .getPrimaryDepartment(userId)
            .orElseThrow(() -> new IllegalArgumentException("Primary department not found"));

    return ResponseEntity.ok(ApiResponse.success(primary, "Primary department set successfully"));
  }

  @DeleteMapping("/{departmentId}")
  public ResponseEntity<ApiResponse<Void>> removeAssignment(
      @PathVariable UUID userId, @PathVariable UUID departmentId) {
    log.info("Removing department assignment: userId={}, departmentId={}", userId, departmentId);

    userDepartmentFacade.removeAssignment(userId, departmentId);

    return ResponseEntity.ok(
        ApiResponse.success(null, "Department assignment removed successfully"));
  }
}
