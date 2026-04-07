package com.fabricmanagement.approval.api;

import com.fabricmanagement.approval.app.ApprovalPolicyService;
import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.approval.domain.ApprovalPolicy;
import com.fabricmanagement.approval.dto.ApprovalPolicyResponse;
import com.fabricmanagement.approval.dto.CreatePolicyRequest;
import com.fabricmanagement.approval.dto.UpdatePolicyRequest;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Tenant yöneticilerinin Onay Politikalarını (Policy) yapılandırdığı REST API. */
@RestController
@RequestMapping("/api/v1/approval/policies")
@RequiredArgsConstructor
@Validated
public class ApprovalPolicyController {

  private final ApprovalPolicyService policyService;

  @GetMapping
  @PreAuthorize("hasAuthority('TENANT_ADMIN') or hasAuthority('HR')")
  public ResponseEntity<ApiResponse<List<ApprovalPolicyResponse>>> getAllPolicies() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    List<ApprovalPolicyResponse> response =
        policyService.getAllPolicies(tenantId).stream().map(ApprovalPolicyResponse::from).toList();
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/{entityType}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<ApprovalPolicyResponse>> getActivePolicy(
      @PathVariable ApprovalEntityType entityType) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return ResponseEntity.ok(
        ApiResponse.success(
            ApprovalPolicyResponse.from(
                policyService
                    .getActivePolicyFor(tenantId, entityType)
                    .orElseThrow(
                        () ->
                            new EntityNotFoundException(
                                "Active approval policy not found for: " + entityType)))));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('TENANT_ADMIN')")
  public ResponseEntity<ApiResponse<ApprovalPolicyResponse>> createPolicy(
      @RequestBody @Valid CreatePolicyRequest req) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    ApprovalPolicy policy =
        policyService.createPolicy(
            tenantId,
            req.getEntityType(),
            req.getRequiredLevel(),
            req.getApproverRole(),
            req.getPromotionThreshold());

    return ResponseEntity.ok(ApiResponse.success(ApprovalPolicyResponse.from(policy)));
  }

  @PutMapping("/{policyId}")
  @PreAuthorize("hasAuthority('TENANT_ADMIN')")
  public ResponseEntity<ApiResponse<ApprovalPolicyResponse>> updatePolicy(
      @PathVariable UUID policyId, @RequestBody @Valid UpdatePolicyRequest req) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    ApprovalPolicy policy =
        policyService.updatePolicy(
            tenantId,
            policyId,
            req.getRequiredLevel(),
            req.getApproverRole(),
            req.getPromotionThreshold());

    return ResponseEntity.ok(ApiResponse.success(ApprovalPolicyResponse.from(policy)));
  }

  @PatchMapping("/{policyId}/active")
  @PreAuthorize("hasAuthority('TENANT_ADMIN')")
  public ResponseEntity<ApiResponse<ApprovalPolicyResponse>> togglePolicy(
      @PathVariable UUID policyId, @RequestParam boolean active) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    ApprovalPolicy policy = policyService.togglePolicy(tenantId, policyId, active);
    return ResponseEntity.ok(ApiResponse.success(ApprovalPolicyResponse.from(policy)));
  }
}
