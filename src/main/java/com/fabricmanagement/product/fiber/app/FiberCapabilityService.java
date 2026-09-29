package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberStatus;
import com.fabricmanagement.product.fiber.dto.FiberAction;
import com.fabricmanagement.product.fiber.dto.FiberActionBlockedReason;
import com.fabricmanagement.product.fiber.dto.FiberActionCapabilityDto;
import com.fabricmanagement.product.fiber.dto.FiberActionRoute;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Backend action capabilities for fibre rows (FIBER-CATALOG-1).
 *
 * <p>Shared catalogue records deny UPDATE, DEACTIVATE and DECLARE_SOURCE with {@code
 * FIBER_SHARED_READ_ONLY} regardless of tenant permissions; source declaration is routed to the
 * reviewed request flow instead. Other reasons reflect the caller's {@code fiber:write} permission
 * and the row's state. Capabilities are display data only: every command validates again.
 */
@Service
@RequiredArgsConstructor
public class FiberCapabilityService {

  private final SpELPermissionEvaluator permissionEvaluator;

  /** Whether the current caller holds {@code fiber:write}; false without an authentication. */
  public boolean currentCallerCanWrite() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && permissionEvaluator.can(authentication, "fiber", "write");
  }

  public List<FiberActionCapabilityDto> capabilities(
      Fiber fiber, UUID readingTenantId, boolean canWrite) {
    boolean sharedForReader =
        FiberCatalog.isOwner(fiber.getTenantId()) && !FiberCatalog.isOwner(readingTenantId);
    if (sharedForReader) {
      return List.of(
          FiberActionCapabilityDto.denied(
              FiberAction.UPDATE, FiberActionBlockedReason.FIBER_SHARED_READ_ONLY),
          FiberActionCapabilityDto.denied(
              FiberAction.DEACTIVATE, FiberActionBlockedReason.FIBER_SHARED_READ_ONLY),
          new FiberActionCapabilityDto(
              FiberAction.DECLARE_SOURCE,
              false,
              FiberActionBlockedReason.FIBER_SHARED_READ_ONLY,
              FiberActionRoute.FIBER_REQUEST,
              true));
    }
    return List.of(
        capability(FiberAction.UPDATE, fiber, canWrite),
        capability(FiberAction.DEACTIVATE, fiber, canWrite),
        capability(FiberAction.DECLARE_SOURCE, fiber, canWrite));
  }

  private static FiberActionCapabilityDto capability(
      FiberAction action, Fiber fiber, boolean canWrite) {
    FiberActionBlockedReason reason = blockedReason(action, fiber, canWrite);
    return reason == null
        ? FiberActionCapabilityDto.allowed(action)
        : FiberActionCapabilityDto.denied(action, reason);
  }

  private static FiberActionBlockedReason blockedReason(
      FiberAction action, Fiber fiber, boolean canWrite) {
    if (!canWrite) {
      return FiberActionBlockedReason.FIBER_WRITE_PERMISSION_REQUIRED;
    }
    if (!Boolean.TRUE.equals(fiber.getIsActive())) {
      return FiberActionBlockedReason.FIBER_INACTIVE;
    }
    if (action == FiberAction.DEACTIVATE) {
      return null;
    }
    if (fiber.getStatus() == FiberStatus.OBSOLETE) {
      return FiberActionBlockedReason.FIBER_OBSOLETE;
    }
    if (action == FiberAction.DECLARE_SOURCE) {
      if (fiber.isBlended()) {
        return FiberActionBlockedReason.FIBER_BLEND_MATERIAL_SOURCE_FORBIDDEN;
      }
      if (fiber.getMaterialSource() != null) {
        return FiberActionBlockedReason.FIBER_MATERIAL_SOURCE_IMMUTABLE;
      }
    }
    return null;
  }
}
