package com.fabricmanagement.sales.ownership.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.ownership.app.CustomerAccountTeamService;
import com.fabricmanagement.sales.ownership.dto.AddCustomerAccountTeamMemberRequest;
import com.fabricmanagement.sales.ownership.dto.CustomerAccountTeamMemberResponse;
import com.fabricmanagement.sales.ownership.dto.CustomerAccountTeamResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/customers/{customerId}/account-team")
@RequiredArgsConstructor
@Tag(name = "Customer Account Team", description = "Commercial ownership for sales customers")
public class CustomerAccountTeamController {

  private final CustomerAccountTeamService accountTeamService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get a customer's commercial account team")
  public ResponseEntity<ApiResponse<CustomerAccountTeamResponse>> getAccountTeam(
      @PathVariable UUID customerId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            accountTeamService.getAccountTeam(TenantContext.requireTenantId(), customerId)));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Add or reactivate a customer account-team member")
  public ResponseEntity<ApiResponse<CustomerAccountTeamMemberResponse>> addMember(
      @PathVariable UUID customerId,
      @Valid @RequestBody AddCustomerAccountTeamMemberRequest request) {
    CustomerAccountTeamMemberResponse member =
        accountTeamService.addMember(TenantContext.requireTenantId(), customerId, request.userId());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(member));
  }

  @DeleteMapping("/{userId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Deactivate a customer account-team member")
  public ResponseEntity<ApiResponse<Void>> deactivateMember(
      @PathVariable UUID customerId, @PathVariable UUID userId) {
    accountTeamService.deactivateMember(TenantContext.requireTenantId(), customerId, userId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
