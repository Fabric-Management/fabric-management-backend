package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.user.app.UserNavPreferencesService;
import com.fabricmanagement.platform.user.dto.NavPreferencesRequest;
import com.fabricmanagement.platform.user.dto.NavPreferencesResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user nav preferences.
 *
 * <p>GET/PATCH /api/common/users/{id}/nav-preferences. Path {@code id} must equal the authenticated
 * user's ID (JWT), otherwise 403. All responses wrapped in {@link ApiResponse}.
 */
@RestController
@RequestMapping("/api/common/users")
@RequiredArgsConstructor
public class UserNavPreferencesController {

  private final UserNavPreferencesService userNavPreferencesService;

  /**
   * Get nav preferences for the user. Returns 200 with stored or default preferences; never 404.
   *
   * <p>Guard: path {@code id} must equal current user ID → 403 otherwise.
   */
  @PreAuthorize("isAuthenticated()")
  @GetMapping("/{id}/nav-preferences")
  public ResponseEntity<ApiResponse<NavPreferencesResponse>> getNavPreferences(
      @PathVariable UUID id) {
    ensureSelf(id);
    UUID tenantId = TenantContext.getCurrentTenantId();
    NavPreferencesResponse data = userNavPreferencesService.getPreferences(tenantId, id);
    return ResponseEntity.ok(ApiResponse.success(data));
  }

  /**
   * Upsert nav preferences (partial update). Creates row if none exists.
   *
   * <p>Guard: path {@code id} must equal current user ID → 403 otherwise.
   */
  @PreAuthorize("isAuthenticated()")
  @PatchMapping("/{id}/nav-preferences")
  public ResponseEntity<ApiResponse<NavPreferencesResponse>> patchNavPreferences(
      @PathVariable UUID id, @Valid @RequestBody NavPreferencesRequest request) {
    ensureSelf(id);
    UUID tenantId = TenantContext.getCurrentTenantId();
    NavPreferencesResponse data =
        userNavPreferencesService.upsertPreferences(tenantId, id, request);
    return ResponseEntity.ok(ApiResponse.success(data));
  }

  /**
   * Ensures path {@code id} equals the current user. Throws {@link AccessDeniedException} (→ 403
   * via {@link com.fabricmanagement.common.infrastructure.web.exception.GlobalExceptionHandler}) if
   * not.
   */
  private void ensureSelf(UUID id) {
    UUID currentUserId = TenantContext.getCurrentUserId();
    if (currentUserId == null || !currentUserId.equals(id)) {
      throw new AccessDeniedException("You can only access your own nav preferences.");
    }
  }
}
