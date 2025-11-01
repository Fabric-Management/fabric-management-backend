package com.fabricmanagement.common.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.user.app.UserDepartmentService;
import com.fabricmanagement.common.platform.user.domain.UserDepartment;
import com.fabricmanagement.common.platform.user.dto.AssignDepartmentRequest;
import com.fabricmanagement.common.platform.user.dto.UserDepartmentDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/common/users/{userId}/departments")
@RequiredArgsConstructor
@Slf4j
public class UserDepartmentController {

    private final UserDepartmentService userDepartmentService;

    /**
     * Get all departments assigned to a user.
     *
     * <p>TenantContext is automatically set by JwtContextInterceptor.</p>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDepartmentDto>>> getUserDepartments(
            @PathVariable UUID userId) {
        log.debug("Getting user departments: userId={}", userId);

        List<UserDepartmentDto> assignments = userDepartmentService.getUserDepartments(userId)
            .stream()
            .map(UserDepartmentDto::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(assignments));
    }

    @GetMapping("/primary")
    public ResponseEntity<ApiResponse<UserDepartmentDto>> getPrimaryDepartment(
            @PathVariable UUID userId) {
        log.debug("Getting primary department: userId={}", userId);

        UserDepartment primary = userDepartmentService.getPrimaryDepartment(userId)
            .orElseThrow(() -> new IllegalArgumentException("No primary department found"));

        return ResponseEntity.ok(ApiResponse.success(UserDepartmentDto.from(primary)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserDepartmentDto>> assignDepartment(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignDepartmentRequest request) {
        log.info("Assigning department to user: userId={}, departmentId={}, isPrimary={}", 
            userId, request.getDepartmentId(), request.getIsPrimary());

        UserDepartment assignment = userDepartmentService.assignDepartment(
            userId,
            request.getDepartmentId(),
            request.getIsPrimary() != null && request.getIsPrimary(),
            TenantContext.getCurrentUserId()
        );

        return ResponseEntity.ok(ApiResponse.success(
            UserDepartmentDto.from(assignment), 
            "Department assigned successfully"
        ));
    }

    @PutMapping("/{departmentId}/primary")
    public ResponseEntity<ApiResponse<UserDepartmentDto>> setPrimaryDepartment(
            @PathVariable UUID userId,
            @PathVariable UUID departmentId) {
        log.info("Setting primary department: userId={}, departmentId={}", userId, departmentId);

        userDepartmentService.setPrimaryDepartment(userId, departmentId);

        UserDepartment primary = userDepartmentService.getPrimaryDepartment(userId)
            .orElseThrow(() -> new IllegalArgumentException("Primary department not found"));

        return ResponseEntity.ok(ApiResponse.success(
            UserDepartmentDto.from(primary), 
            "Primary department set successfully"
        ));
    }

    @DeleteMapping("/{departmentId}")
    public ResponseEntity<ApiResponse<Void>> removeAssignment(
            @PathVariable UUID userId,
            @PathVariable UUID departmentId) {
        log.info("Removing department assignment: userId={}, departmentId={}", userId, departmentId);

        userDepartmentService.removeAssignment(userId, departmentId);

        return ResponseEntity.ok(ApiResponse.success(null, "Department assignment removed successfully"));
    }
}

