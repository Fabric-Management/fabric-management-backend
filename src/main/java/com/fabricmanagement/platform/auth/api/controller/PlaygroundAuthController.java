package com.fabricmanagement.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.JwtTokenExtractor;
import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.auth.app.PlaygroundService;
import com.fabricmanagement.platform.auth.dto.PlaygroundImpersonateResponse;
import com.fabricmanagement.platform.auth.dto.PlaygroundPersonaDto;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
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
@Tag(
    name = "Playground Auth",
    description = "Demo personas for a registered tenant in demo mode (the Playground)")
@Slf4j
@RequiredArgsConstructor
public class PlaygroundAuthController {

  private final PlaygroundService playgroundService;
  private final JwtService jwtService;

  @PostMapping("/impersonate/{userId}")
  @Operation(
      summary = "Get a token to view the demo as another user",
      description =
          "Demo tenants only. The target must be an active user of the caller's tenant."
              + " Signed-in session: the persona's access token is returned in the body and no"
              + " cookie is written; the calling browser tab sends it as an Authorization header"
              + " and asks again when it expires. The owner's own session stays as it is, so"
              + " other tabs are unaffected. A persona token cannot call this endpoint.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Persona token issued"),
    @ApiResponse(
        responseCode = "403",
        description = "Demo mode is required, or the caller is itself a persona",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "The target is not an active user of this tenant",
        content = @Content)
  })
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<PlaygroundImpersonateResponse> impersonate(
      @Parameter(
              description = "Target user ID to impersonate",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathVariable
          UUID userId,
      HttpServletRequest request) {
    AuthenticatedUserContext ctx = getAuthenticatedContextOrNull();
    if (ctx == null) {
      throw new AccessDeniedException("Authenticated user context is required");
    }

    // Only the signed-in owner may hand out persona tokens. A persona token renewing itself, or
    // minting others, would outlive the owner's decision and the owner's session.
    String callerToken = JwtTokenExtractor.extract(request);
    if (callerToken != null && jwtService.isDemoImpersonationToken(callerToken)) {
      throw new PlatformDomainException(
          "A persona cannot start an impersonation; use the signed-in account",
          "IMPERSONATION_FROM_PERSONA",
          403);
    }

    log.info(
        "Playground impersonation request. guestId: {}, targetUser: {}, tenantId: {}",
        ctx.guestId(),
        userId,
        ctx.tenantId());

    // PLATFORM-BOUNDARY-1: the anonymous Playground is retired; only a signed-in owner of a demo
    // tenant asks for a persona token, which the calling tab keeps for itself.
    return ResponseEntity.ok(playgroundService.impersonate(ctx.tenantId(), userId, ctx.guestId()));
  }

  @GetMapping("/personas")
  @Operation(
      summary = "List the demo personas of the caller's tenant",
      description =
          "Demo tenants only. Returns the tenant's active users that the signed-in owner can view"
              + " the demo as.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "List of personas retrieved successfully"),
    @ApiResponse(responseCode = "403", description = "Demo mode is required", content = @Content)
  })
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<PlaygroundPersonaDto>> listPersonas() {
    AuthenticatedUserContext ctx = getAuthenticatedContextOrNull();
    if (ctx == null) {
      throw new AccessDeniedException("Authenticated user context is required");
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
