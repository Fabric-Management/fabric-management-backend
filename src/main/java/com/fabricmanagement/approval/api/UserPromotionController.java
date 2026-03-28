package com.fabricmanagement.approval.api;

import com.fabricmanagement.approval.app.UserPromotionService;
import com.fabricmanagement.approval.dto.PromotionRejectDto;
import com.fabricmanagement.approval.dto.UserPromotionResponse;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Kullanıcı seviyesi (PROBATION->STANDARD) terfilerini yöneticilerin değerlendirdiği REST API. */
@RestController
@RequestMapping("/api/v1/approval/promotions")
@RequiredArgsConstructor
@Validated
public class UserPromotionController {

  private final UserPromotionService promotionService;

  @GetMapping("/pending")
  @PreAuthorize("hasAuthority('TENANT_ADMIN') or hasAuthority('HR')")
  public ResponseEntity<List<UserPromotionResponse>> getPendingPromotions() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    List<UserPromotionResponse> response =
        promotionService.getPendingPromotions(tenantId).stream()
            .map(UserPromotionResponse::from)
            .toList();
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{promotionId}/approve")
  @PreAuthorize("hasAuthority('TENANT_ADMIN') or hasAuthority('HR')")
  public ResponseEntity<Void> approvePromotion(@PathVariable UUID promotionId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    UUID adminId = TenantContext.getCurrentUserId();

    promotionService.approvePromotion(tenantId, promotionId, adminId);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{promotionId}/reject")
  @PreAuthorize("hasAuthority('TENANT_ADMIN') or hasAuthority('HR')")
  public ResponseEntity<Void> rejectPromotion(
      @PathVariable UUID promotionId,
      @RequestBody(required = false) @Valid PromotionRejectDto dto) {

    UUID tenantId = TenantContext.getCurrentTenantId();
    UUID adminId = TenantContext.getCurrentUserId();
    String note = dto != null ? dto.getReason() : null;

    promotionService.rejectPromotion(tenantId, promotionId, adminId, note);
    return ResponseEntity.ok().build();
  }
}
