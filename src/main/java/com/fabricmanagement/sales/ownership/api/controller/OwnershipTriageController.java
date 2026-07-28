package com.fabricmanagement.sales.ownership.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.sales.ownership.app.CustomerCommercialAssignmentService;
import com.fabricmanagement.sales.ownership.app.OwnershipTriageService;
import com.fabricmanagement.sales.ownership.domain.ActorRef;
import com.fabricmanagement.sales.ownership.domain.AssignmentSource;
import com.fabricmanagement.sales.ownership.dto.AssignPrimaryRepresentativeRequest;
import com.fabricmanagement.sales.ownership.dto.CustomerCommercialAssignmentResponse;
import com.fabricmanagement.sales.ownership.dto.OwnershipTriageCaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/sales/ownership/triage")
@RequiredArgsConstructor
@Tag(
    name = "Sales Ownership Triage",
    description = "Derived queue of customers requiring a commercial representative")
public class OwnershipTriageController {

  private final OwnershipTriageService triageService;
  private final CustomerCommercialAssignmentService assignmentService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "List current sales ownership triage cases")
  public ResponseEntity<ApiResponse<PagedResponse<OwnershipTriageCaseResponse>>> list(
      @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(triageService.list(TenantContext.requireTenantId(), pageable))));
  }

  @PostMapping("/{customerId}/resolve")
  @PreAuthorize(
      "@auth.can(authentication, 'sales', 'write')" + " and hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Resolve a triage case by assigning its primary representative")
  public ResponseEntity<ApiResponse<CustomerCommercialAssignmentResponse>> resolve(
      @PathVariable UUID customerId,
      @Valid @RequestBody AssignPrimaryRepresentativeRequest request) {
    UUID actorId =
        Objects.requireNonNull(
            TenantContext.getCurrentUserId(), "Current user is required for triage resolution");
    CustomerCommercialAssignmentResponse response =
        CustomerCommercialAssignmentResponse.from(
            assignmentService.assignPrimary(
                TenantContext.requireTenantId(),
                customerId,
                request.representativeId(),
                AssignmentSource.TRIAGE_RESOLUTION,
                ActorRef.user(actorId)));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }
}
