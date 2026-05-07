package com.fabricmanagement.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.auth.dto.PlaygroundImpersonateResponse;
import com.fabricmanagement.platform.auth.dto.PlaygroundInitResponse;
import com.fabricmanagement.platform.auth.dto.PlaygroundPersonaDto;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/playground")
@Tag(name = "Playground Auth", description = "Endpoints for PLG Simulation Playground")
@Slf4j
public class PlaygroundAuthController {

  private final TenantClonerService tenantClonerService;
  private final JwtService jwtService;
  private final UserRepository userRepository;

  /**
   * IP-based rate limiter for playground initialization. Entries auto-expire after 1 minute via
   * Caffeine's expireAfterWrite — no manual cleanup needed (CR2-4).
   */
  private final Cache<String, Instant> rateLimitCache =
      Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).maximumSize(10_000).build();

  public PlaygroundAuthController(
      TenantClonerService tenantClonerService,
      JwtService jwtService,
      UserRepository userRepository) {
    this.tenantClonerService = tenantClonerService;
    this.jwtService = jwtService;
    this.userRepository = userRepository;
  }

  @PostMapping("/init")
  @Operation(summary = "Initialize a new playground session")
  public ResponseEntity<?> initPlayground(
      @RequestParam(required = false) String guestId, HttpServletRequest request) {

    // Rate Limiting — Caffeine auto-evicts entries after 1 minute
    String clientIp = request.getRemoteAddr();
    Instant lastInit = rateLimitCache.getIfPresent(clientIp);
    if (lastInit != null) {
      log.warn("Rate limit exceeded for playground init. IP: {}", clientIp);
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(
              Map.of(
                  "error", "Too many playground initialization requests. Please wait 1 minute."));
    }
    rateLimitCache.put(clientIp, Instant.now());

    final String finalGuestId =
        (guestId == null || guestId.isBlank()) ? UUID.randomUUID().toString() : guestId;

    log.info("Initializing playground session for guest: {} from IP: {}", finalGuestId, clientIp);

    // 1. Clone the template
    Tenant playgroundTenant = tenantClonerService.cloneTemplateToPlayground();

    // 2. Find the CEO / Platform Admin to be the default persona
    return TenantContext.executeInTenantContext(
        playgroundTenant.getId(),
        () -> {
          User defaultUser =
              userRepository.findByTenantIdAndIsActiveTrue(playgroundTenant.getId()).stream()
                  .filter(
                      u ->
                          u.getRole() != null
                              && ("PLATFORM_ADMIN".equals(u.getRole().getRoleCode())
                                  || "MANAGER".equals(u.getRole().getRoleCode())))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "No default persona found in cloned playground tenant"));

          String token = jwtService.generatePlaygroundAccessToken(defaultUser, finalGuestId);
          String roleName =
              defaultUser.getRole() != null ? defaultUser.getRole().getRoleName() : "No Role";

          return ResponseEntity.ok(
              new PlaygroundInitResponse(
                  finalGuestId,
                  token,
                  playgroundTenant.getId(),
                  defaultUser.getId(),
                  defaultUser.getDisplayName(),
                  roleName));
        });
  }

  @PostMapping("/impersonate/{userId}")
  @Operation(summary = "Switch persona in the current playground session")
  public ResponseEntity<?> impersonate(@PathVariable UUID userId) {
    AuthenticatedUserContext ctx = getAuthenticatedContextOrNull();
    if (ctx == null || !ctx.isPlayground()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(Map.of("error", "Not a valid playground session"));
    }

    String guestId = ctx.guestId();
    UUID tenantId = ctx.tenantId();

    log.info(
        "Playground impersonation request. guestId: {}, targetUser: {}, tenantId: {}",
        guestId,
        userId,
        tenantId);

    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          User targetUser =
              userRepository
                  .findById(userId)
                  .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

          if (!targetUser.getTenantId().equals(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Cannot impersonate user outside of playground tenant"));
          }

          String newToken = jwtService.generatePlaygroundAccessToken(targetUser, guestId);
          String roleName =
              targetUser.getRole() != null ? targetUser.getRole().getRoleName() : "No Role";

          return ResponseEntity.ok(
              new PlaygroundImpersonateResponse(
                  newToken, targetUser.getId(), targetUser.getDisplayName(), roleName));
        });
  }

  @GetMapping("/personas")
  @Operation(summary = "List all available personas in the current playground session")
  public ResponseEntity<List<PlaygroundPersonaDto>> listPersonas() {
    AuthenticatedUserContext ctx = getAuthenticatedContextOrNull();
    if (ctx == null || !ctx.isPlayground()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    UUID tenantId = ctx.tenantId();

    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          List<User> users = userRepository.findByTenantIdWithRelations(tenantId);

          var personas =
              users.stream()
                  .map(
                      u ->
                          new PlaygroundPersonaDto(
                              u.getId(),
                              u.getDisplayName(),
                              u.getRole() != null ? u.getRole().getRoleName() : "No Role",
                              u.getUserDepartments().stream()
                                  .filter(ud -> Boolean.TRUE.equals(ud.getIsPrimary()))
                                  .map(ud -> ud.getDepartment().getDepartmentName())
                                  .findFirst()
                                  .orElse("No Department")))
                  .toList();

          return ResponseEntity.ok(personas);
        });
  }

  private AuthenticatedUserContext getAuthenticatedContextOrNull() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthenticatedUserContext ctx) {
      return ctx;
    }
    return null;
  }
}
