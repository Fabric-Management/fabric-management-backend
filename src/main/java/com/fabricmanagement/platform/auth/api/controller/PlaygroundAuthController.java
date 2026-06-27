package com.fabricmanagement.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthCookieSupport;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.exception.TooManyRequestsException;
import com.fabricmanagement.platform.auth.app.PlaygroundService;
import com.fabricmanagement.platform.auth.dto.PlaygroundImpersonateResponse;
import com.fabricmanagement.platform.auth.dto.PlaygroundInitResponse;
import com.fabricmanagement.platform.auth.dto.PlaygroundPersonaDto;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/playground")
@Tag(name = "Playground Auth", description = "Endpoints for PLG Simulation Playground")
@Slf4j
@RequiredArgsConstructor
public class PlaygroundAuthController {

  private final PlaygroundService playgroundService;
  private final AuthCookieSupport authCookieSupport;

  /**
   * IP-based rate limiter for playground initialization. Entries auto-expire after 10 seconds via
   * Caffeine's expireAfterWrite — no manual cleanup needed.
   *
   * <p>Non-final: excluded from Lombok @RequiredArgsConstructor to avoid Spring DI failure (no
   * Cache bean in context).
   */
  private Cache<String, Instant> rateLimitCache =
      Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).maximumSize(10_000).build();

  /**
   * @deprecated Register-first playground tenants with {@code demoMode} are the supported entry;
   *     anonymous init is retired pending FE migration.
   */
  @Deprecated
  @PostMapping("/init")
  @Operation(
      summary = "Initialize a new playground session",
      deprecated = true,
      description =
          "Clones the TEMPLATE tenant into an ephemeral PLAYGROUND tenant, "
              + "assigns the first PLATFORM_ADMIN user as the default persona, "
              + "and returns a JWT token scoped to the playground context. "
              + "Rate limited to 1 request per IP per minute.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Playground initialized successfully"),
    @ApiResponse(
        responseCode = "429",
        description = "Rate limit exceeded for initialization",
        content = @Content)
  })
  @PreAuthorize("permitAll()")
  public ResponseEntity<PlaygroundInitResponse> initPlayground(
      @Parameter(
              description = "Optional guest identifier. Auto-generated if omitted.",
              example = "demo-visitor-42")
          @RequestParam(required = false)
          String guestId,
      HttpServletRequest request,
      HttpServletResponse response) {

    // Rate Limiting — Caffeine auto-evicts entries after 1 minute
    String clientIp = request.getRemoteAddr();
    Instant lastInit = rateLimitCache.getIfPresent(clientIp);
    if (lastInit != null) {
      log.warn("Rate limit exceeded for playground init. IP: {}", clientIp);
      throw new TooManyRequestsException(
          "Too many playground initialization requests. Please wait a few seconds.");
    }

    final String finalGuestId =
        (guestId == null || guestId.isBlank()) ? UUID.randomUUID().toString() : guestId;

    log.info("Initializing playground session for guest: {} from IP: {}", finalGuestId, clientIp);

    PlaygroundInitResponse initResponse = playgroundService.initPlayground(finalGuestId);

    // Set JWT as HttpOnly cookie — frontend uses cookie-based auth exclusively
    authCookieSupport.addAuthCookies(response, initResponse.token(), null);

    // Success! Now record the rate limit to prevent spam
    rateLimitCache.put(clientIp, Instant.now());

    return ResponseEntity.ok(initResponse);
  }

  @PostMapping("/impersonate/{userId}")
  @Operation(
      summary = "Switch persona in the current playground session",
      description =
          "Validates the target user belongs to the current playground tenant and generates a new JWT token for that persona.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Persona switched successfully"),
    @ApiResponse(
        responseCode = "403",
        description = "Not a valid playground session or user outside playground",
        content = @Content)
  })
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<PlaygroundImpersonateResponse> impersonate(
      @Parameter(
              description = "Target user ID to impersonate",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathVariable
          UUID userId,
      HttpServletResponse response) {
    AuthenticatedUserContext ctx = getAuthenticatedContextOrNull();
    if (ctx == null || !ctx.isPlayground()) {
      throw new AccessDeniedException("Not a valid playground session");
    }

    log.info(
        "Playground impersonation request. guestId: {}, targetUser: {}, tenantId: {}",
        ctx.guestId(),
        userId,
        ctx.tenantId());

    PlaygroundImpersonateResponse impersonateResponse =
        playgroundService.impersonate(ctx.tenantId(), userId, ctx.guestId());

    // Set JWT as HttpOnly cookie — frontend uses cookie-based auth exclusively
    authCookieSupport.addAuthCookies(response, impersonateResponse.token(), null);

    return ResponseEntity.ok(impersonateResponse);
  }

  @GetMapping("/personas")
  @Operation(
      summary = "List all available personas in the current playground session",
      description =
          "Returns a list of all active users in the cloned playground tenant that can be impersonated.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "List of personas retrieved successfully"),
    @ApiResponse(
        responseCode = "403",
        description = "Not a valid playground session",
        content = @Content)
  })
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<PlaygroundPersonaDto>> listPersonas() {
    AuthenticatedUserContext ctx = getAuthenticatedContextOrNull();
    if (ctx == null || !ctx.isPlayground()) {
      throw new AccessDeniedException("Not a valid playground session");
    }

    List<PlaygroundPersonaDto> personas = playgroundService.listPersonas(ctx.tenantId());
    return ResponseEntity.ok(personas);
  }

  private AuthenticatedUserContext getAuthenticatedContextOrNull() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthenticatedUserContext ctx) {
      return ctx;
    }
    return null;
  }
}
