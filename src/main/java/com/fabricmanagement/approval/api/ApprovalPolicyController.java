package com.fabricmanagement.approval.api;

import com.fabricmanagement.approval.app.ApprovalPolicyService;
import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.approval.domain.ApprovalPolicy;
import com.fabricmanagement.approval.dto.ApprovalPolicyResponse;
import com.fabricmanagement.approval.dto.CreatePolicyRequest;
import com.fabricmanagement.approval.dto.UpdatePolicyRequest;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Approval Policy", description = "Onay Politikası Yönetimi")
@RestController
@RequestMapping("/api/v1/approval/policies")
@RequiredArgsConstructor
@Validated
public class ApprovalPolicyController {

  private final ApprovalPolicyService policyService;

  @Operation(summary = "Tüm onay politikalarını getirir")
  @GetMapping
  @PreAuthorize("hasAuthority('TENANT_ADMIN') or hasAuthority('HR')")
  public ResponseEntity<ApiResponse<List<ApprovalPolicyResponse>>> getAllPolicies() {
    UUID tenantId = TenantContext.requireTenantId();
    List<ApprovalPolicyResponse> response =
        policyService.getAllPolicies(tenantId).stream().map(ApprovalPolicyResponse::from).toList();
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "Belirli bir entity tipi için aktif olan politikayı getirir")
  @GetMapping("/{entityType}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<ApprovalPolicyResponse>> getActivePolicy(
      @PathVariable ApprovalEntityType entityType) {
    UUID tenantId = TenantContext.requireTenantId();
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

  @Operation(summary = "Yeni bir onay politikası oluşturur")
  @PostMapping
  @PreAuthorize("hasAuthority('TENANT_ADMIN')")
  public ResponseEntity<ApiResponse<ApprovalPolicyResponse>> createPolicy(
      @RequestBody @Valid CreatePolicyRequest req) {
    UUID tenantId = TenantContext.requireTenantId();
    ApprovalPolicy policy =
        policyService.createPolicy(
            tenantId,
            req.getEntityType(),
            req.getRequiredLevel(),
            req.getApproverRole(),
            req.getPromotionThreshold(),
            req.getExpiryHours());

    return ResponseEntity.ok(ApiResponse.success(ApprovalPolicyResponse.from(policy)));
  }

  @Operation(summary = "Mevcut bir onay politikasını günceller")
  @PutMapping("/{policyId}")
  @PreAuthorize("hasAuthority('TENANT_ADMIN')")
  public ResponseEntity<ApiResponse<ApprovalPolicyResponse>> updatePolicy(
      @PathVariable UUID policyId, @RequestBody @Valid UpdatePolicyRequest req) {
    UUID tenantId = TenantContext.requireTenantId();
    ApprovalPolicy policy =
        policyService.updatePolicy(
            tenantId,
            policyId,
            req.getRequiredLevel(),
            req.getApproverRole(),
            req.getPromotionThreshold(),
            req.getExpiryHours());

    return ResponseEntity.ok(ApiResponse.success(ApprovalPolicyResponse.from(policy)));
  }

  @Operation(summary = "Mevcut bir onay politikasını aktif/pasif yapar")
  @PatchMapping("/{policyId}/active")
  @PreAuthorize("hasAuthority('TENANT_ADMIN')")
  public ResponseEntity<ApiResponse<ApprovalPolicyResponse>> togglePolicy(
      @PathVariable UUID policyId, @RequestParam boolean active) {
    UUID tenantId = TenantContext.requireTenantId();
    ApprovalPolicy policy = policyService.togglePolicy(tenantId, policyId, active);
    return ResponseEntity.ok(ApiResponse.success(ApprovalPolicyResponse.from(policy)));
  }
}
