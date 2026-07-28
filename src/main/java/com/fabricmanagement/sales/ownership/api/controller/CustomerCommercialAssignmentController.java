package com.fabricmanagement.sales.ownership.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.ownership.app.CustomerCommercialAssignmentService;
import com.fabricmanagement.sales.ownership.domain.ActorRef;
import com.fabricmanagement.sales.ownership.domain.AssignmentSource;
import com.fabricmanagement.sales.ownership.dto.AssignPrimaryRepresentativeRequest;
import com.fabricmanagement.sales.ownership.dto.CustomerCommercialAssignmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/customers/{customerId}/commercial-assignments")
@RequiredArgsConstructor
@Tag(
    name = "Customer Commercial Assignments",
    description = "Effective-dated primary commercial representative history")
public class CustomerCommercialAssignmentController {

  private final CustomerCommercialAssignmentService assignmentService;

  @GetMapping("/current")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get the current commercial assignment")
  public ResponseEntity<ApiResponse<CustomerCommercialAssignmentResponse>> getCurrent(
      @PathVariable UUID customerId) {
    CustomerCommercialAssignmentResponse response =
        assignmentService
            .getCurrentAssignment(TenantContext.requireTenantId(), customerId)
            .orElse(null);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/history")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get effective-dated commercial assignment history")
  public ResponseEntity<ApiResponse<List<CustomerCommercialAssignmentResponse>>> getHistory(
      @PathVariable UUID customerId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            assignmentService.getAssignmentHistory(TenantContext.requireTenantId(), customerId)));
  }

  @PostMapping
  @PreAuthorize(
      "@auth.can(authentication, 'sales', 'write')" + " and hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Assign the primary commercial representative")
  public ResponseEntity<ApiResponse<CustomerCommercialAssignmentResponse>> assignPrimary(
      @PathVariable UUID customerId,
      @Valid @RequestBody AssignPrimaryRepresentativeRequest request) {
    UUID actorId =
        Objects.requireNonNull(
            TenantContext.getCurrentUserId(), "Current user is required for manual assignment");
    CustomerCommercialAssignmentResponse response =
        CustomerCommercialAssignmentResponse.from(
            assignmentService.assignPrimary(
                TenantContext.requireTenantId(),
                customerId,
                request.representativeId(),
                AssignmentSource.MANUAL,
                ActorRef.user(actorId)));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }
}
