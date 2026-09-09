package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.user.app.PermissionCatalogueService;
import com.fabricmanagement.platform.user.dto.PermissionCatalogueEntryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/permissions/catalogue")
@RequiredArgsConstructor
@Tag(name = "Permission Catalogue", description = "Canonical permission vocabulary")
public class PermissionCatalogueController {
  private final PermissionCatalogueService permissionCatalogueService;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List the permission catalogue",
      description =
          "Available to every authenticated session. Returns vocabulary, not effective grants.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Complete catalogue ordered by key")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "401",
      description = "Authentication required",
      content = @Content)
  public ResponseEntity<ApiResponse<List<PermissionCatalogueEntryDto>>> catalogue() {
    return ResponseEntity.ok(ApiResponse.success(permissionCatalogueService.catalogue()));
  }
}
