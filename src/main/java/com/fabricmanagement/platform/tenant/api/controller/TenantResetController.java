package com.fabricmanagement.platform.tenant.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.tenant.app.TenantResetService;
import com.fabricmanagement.platform.tenant.dto.ResetDemoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tenant Demo Reset", description = "Demo tenant reset operations")
public class TenantResetController {

  private final TenantResetService tenantResetService;

  @PostMapping("/reset-demo")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Reset demo data",
      description =
          "Requires a demo-mode tenant owner. Purges sandbox demo data and restores fresh sample personas and business data while keeping the tenant in demo mode.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Demo reset with fresh sample data",
        content = @Content(schema = @Schema(implementation = ResetDemoResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "DEMO_MODE_REQUIRED or RESET_REQUIRES_OWNER",
        content = @Content)
  })
  public ResponseEntity<ApiResponse<ResetDemoResponse>> resetDemo() {
    AuthenticatedUserContext ctx = currentUser();
    log.info("Demo reset requested: tenantId={}, userId={}", ctx.tenantId(), ctx.userId());

    ResetDemoResponse response = tenantResetService.reset(ctx);
    return ResponseEntity.ok(ApiResponse.success(response, "Demo reset with fresh sample data"));
  }

  private AuthenticatedUserContext currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof AuthenticatedUserContext ctx) {
      return ctx;
    }
    if (auth != null && auth.getPrincipal() instanceof AuthenticatedUserContext ctx) {
      return ctx;
    }
    throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
  }
}
