package com.fabricmanagement.platform.tenant.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.tenant.app.TenantGoRealService;
import com.fabricmanagement.platform.tenant.dto.GoRealResponse;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tenant Go Real", description = "Tenant go-real transition operations")
public class TenantGoRealController {

  private final TenantGoRealService tenantGoRealService;

  @PostMapping("/go-real")
  public ResponseEntity<ApiResponse<GoRealResponse>> goReal(
      @RequestBody(required = false) @Valid CompleteOnboardingRequest request) {
    AuthenticatedUserContext ctx = currentUser();
    log.info("Go-real requested: tenantId={}, userId={}", ctx.tenantId(), ctx.userId());

    GoRealResponse response = tenantGoRealService.goReal(ctx, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Tenant switched to real mode"));
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
