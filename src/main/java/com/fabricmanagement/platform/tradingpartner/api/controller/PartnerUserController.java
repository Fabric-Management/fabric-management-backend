package com.fabricmanagement.platform.tradingpartner.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.tradingpartner.app.PartnerUserService;
import com.fabricmanagement.platform.tradingpartner.dto.InvitePartnerUserRequest;
import com.fabricmanagement.platform.tradingpartner.dto.PartnerUserDto;
import com.fabricmanagement.platform.tradingpartner.dto.UpdatePartnerUserRoleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing partner portal users.
 *
 * <p>All endpoints are scoped to a specific {@code TradingPartner} via {@code partnerId}. Access
 * requires {@code ADMIN} or {@code TRADING_PARTNER_MANAGER} role — these are internal tenant users
 * managing their partners.
 *
 * <p>Partner isolation is enforced at the service layer: the {@code partnerId} path variable must
 * map to a TradingPartner owned by the current tenant, and the target user must belong to that
 * partner's organisation.
 */
@RestController
@RequestMapping("/api/common/trading-partners/{partnerId}/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Partner Users", description = "Partner portal user management")
public class PartnerUserController {

  private final PartnerUserService partnerUserService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TRADING_PARTNER_MANAGER')")
  @Operation(
      summary = "List partner users",
      description = "Returns all portal users for a trading partner")
  public ResponseEntity<ApiResponse<List<PartnerUserDto>>> listUsers(@PathVariable UUID partnerId) {
    log.debug("Listing partner users: partnerId={}", partnerId);
    List<PartnerUserDto> users = partnerUserService.listUsers(partnerId);
    return ResponseEntity.ok(ApiResponse.success(users));
  }

  @PostMapping("/invite")
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(
      summary = "Invite partner user",
      description = "Creates a new user and sends a partner portal invitation email")
  public ResponseEntity<ApiResponse<PartnerUserDto>> inviteUser(
      @PathVariable UUID partnerId, @Valid @RequestBody InvitePartnerUserRequest request) {
    log.info("Inviting partner user: partnerId={}", partnerId);
    PartnerUserDto created = partnerUserService.inviteUser(partnerId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
  }

  @PutMapping("/{userId}/role")
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(summary = "Update partner user role")
  public ResponseEntity<ApiResponse<PartnerUserDto>> updateRole(
      @PathVariable UUID partnerId,
      @PathVariable UUID userId,
      @Valid @RequestBody UpdatePartnerUserRoleRequest request) {
    log.info("Updating partner user role: partnerId={}, userId={}", partnerId, userId);
    PartnerUserDto updated = partnerUserService.updateRole(partnerId, userId, request);
    return ResponseEntity.ok(ApiResponse.success(updated));
  }

  @PostMapping("/{userId}/suspend")
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(summary = "Suspend partner user")
  public ResponseEntity<ApiResponse<Void>> suspendUser(
      @PathVariable UUID partnerId, @PathVariable UUID userId) {
    log.info("Suspending partner user: partnerId={}, userId={}", partnerId, userId);
    partnerUserService.suspendUser(partnerId, userId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @PostMapping("/{userId}/reactivate")
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(summary = "Reactivate partner user")
  public ResponseEntity<ApiResponse<PartnerUserDto>> reactivateUser(
      @PathVariable UUID partnerId, @PathVariable UUID userId) {
    log.info("Reactivating partner user: partnerId={}, userId={}", partnerId, userId);
    PartnerUserDto reactivated = partnerUserService.reactivateUser(partnerId, userId);
    return ResponseEntity.ok(ApiResponse.success(reactivated));
  }

  @DeleteMapping("/{userId}")
  @PreAuthorize("hasAnyRole('ADMIN')")
  @Operation(summary = "Remove partner user permanently")
  public ResponseEntity<ApiResponse<Void>> removeUser(
      @PathVariable UUID partnerId, @PathVariable UUID userId) {
    log.info("Removing partner user: partnerId={}, userId={}", partnerId, userId);
    partnerUserService.removeUser(partnerId, userId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
