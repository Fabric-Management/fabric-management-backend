package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.user.app.UserUiPreferenceService;
import com.fabricmanagement.platform.user.dto.UiPreferenceRequest;
import com.fabricmanagement.platform.user.dto.UiPreferenceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for a user's interface preferences, e.g. the order of a page's sections.
 *
 * <p>GET/PUT /api/v1/common/users/{id}/ui-preferences/{key}. Path {@code id} must equal the
 * authenticated user's ID (JWT), otherwise 403. All responses wrapped in {@link ApiResponse}.
 */
@RestController
@RequestMapping("/api/v1/common/users")
@RequiredArgsConstructor
@Tag(name = "User UI Preferences", description = "User interface preference operations")
public class UserUiPreferenceController {

  private final UserUiPreferenceService userUiPreferenceService;

  @Operation(
      summary = "Get an interface preference of the current user",
      description =
          "Returns the values stored under the key, or an empty list when none were saved. "
              + "Returns 403 for another user and 400 for an invalid key.")
  @PreAuthorize("isAuthenticated()")
  @GetMapping(value = "/{id}/ui-preferences/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ApiResponse<UiPreferenceResponse>> getUiPreference(
      @PathVariable UUID id, @PathVariable String key) {
    ensureSelf(id);
    UUID tenantId = TenantContext.requireTenantId();
    return ResponseEntity.ok(
        ApiResponse.success(userUiPreferenceService.getPreference(tenantId, id, key)));
  }

  @Operation(
      summary = "Save an interface preference of the current user",
      description =
          "Replaces the values stored under the key, creating the preference when needed. "
              + "Returns 403 for another user, 404 for a user absent from the tenant, "
              + "and 400 for an invalid key or values.")
  @PreAuthorize("isAuthenticated()")
  @PutMapping(value = "/{id}/ui-preferences/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ApiResponse<UiPreferenceResponse>> putUiPreference(
      @PathVariable UUID id,
      @PathVariable String key,
      @Valid @RequestBody UiPreferenceRequest request) {
    ensureSelf(id);
    UUID tenantId = TenantContext.requireTenantId();
    return ResponseEntity.ok(
        ApiResponse.success(
            userUiPreferenceService.savePreference(tenantId, id, key, request.values())));
  }

  /**
   * Ensures path {@code id} equals the current user. Throws {@link AccessDeniedException} (→ 403
   * via {@link com.fabricmanagement.common.infrastructure.web.exception.GlobalExceptionHandler}) if
   * not.
   */
  private void ensureSelf(UUID id) {
    UUID currentUserId = TenantContext.getCurrentUserId();
    if (currentUserId == null || !currentUserId.equals(id)) {
      throw new AccessDeniedException("You can only access your own interface preferences.");
    }
  }
}
