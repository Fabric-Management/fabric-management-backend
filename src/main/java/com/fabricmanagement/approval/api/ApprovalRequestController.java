package com.fabricmanagement.approval.api;

import com.fabricmanagement.approval.app.ApprovalRequestService;
import com.fabricmanagement.approval.dto.ApprovalRequestResponse;
import com.fabricmanagement.approval.dto.RejectRequestDto;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Onay bekleyen işlemleri amirlerin/yöneticilerin görüp cevapladığı REST API. */
@RestController
@RequestMapping("/api/v1/approval/requests")
@RequiredArgsConstructor
@Validated
public class ApprovalRequestController {

  private final ApprovalRequestService requestService;

  @GetMapping("/pending")
  @PreAuthorize("hasAuthority('TENANT_ADMIN') or hasAuthority('HR') or hasAuthority('MANAGER')")
  public ResponseEntity<List<ApprovalRequestResponse>> getPendingRequests(
      @RequestParam(required = false) String approverRole) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    List<ApprovalRequestResponse> response =
        requestService.getPendingRequests(tenantId, approverRole).stream()
            .map(ApprovalRequestResponse::from)
            .toList();
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{requestId}/approve")
  @PreAuthorize("hasAuthority('TENANT_ADMIN') or hasAuthority('HR') or hasAuthority('MANAGER')")
  public ResponseEntity<Void> approveRequest(@PathVariable UUID requestId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    UUID currentUserId = TenantContext.getCurrentUserId();

    requestService.approveRequest(tenantId, requestId, currentUserId);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{requestId}/reject")
  @PreAuthorize("hasAuthority('TENANT_ADMIN') or hasAuthority('HR') or hasAuthority('MANAGER')")
  public ResponseEntity<Void> rejectRequest(
      @PathVariable UUID requestId, @RequestBody @Valid RejectRequestDto dto) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    UUID currentUserId = TenantContext.getCurrentUserId();

    requestService.rejectRequest(tenantId, requestId, currentUserId, dto.getReason());
    return ResponseEntity.ok().build();
  }
}
