package com.fabricmanagement.platform.tenant.api.controller;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.tenant.app.TenantBrandingService;
import com.fabricmanagement.platform.tenant.domain.BrandAppearance;
import com.fabricmanagement.platform.tenant.domain.BrandAssetKind;
import com.fabricmanagement.platform.tenant.domain.BrandColourMode;
import com.fabricmanagement.platform.tenant.dto.TenantBrandAssetDto;
import com.fabricmanagement.platform.tenant.dto.TenantBrandingDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/common/tenant/settings/branding")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tenant Branding")
public class TenantBrandingController {
  private final TenantBrandingService service;
  private final SpELPermissionEvaluator auth;

  @GetMapping
  @Operation(
      operationId = "readTenantBranding",
      summary = "Read branding of the authenticated tenant")
  public ResponseEntity<ApiResponse<TenantBrandingDto>> read(Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(service.read(auth.can(authentication, "members", "manage"))));
  }

  @PostMapping(value = "/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      operationId = "uploadTenantBrandAsset",
      summary = "Upload or replace a tenant SVG (512 KB maximum; administrators only)")
  public ResponseEntity<ApiResponse<TenantBrandAssetDto>> upload(
      @PathVariable BrandAssetKind kind,
      @RequestParam(defaultValue = "ORIGINAL") BrandColourMode colourMode,
      @Parameter(
              description =
                  "Static SVG with viewBox; text must be converted to outlines. Maximum 524288"
                      + " bytes.",
              required = true,
              schema = @Schema(type = "string", format = "binary"))
          @RequestPart("file")
          MultipartFile file,
      Authentication authentication) {
    requireAdministrator(authentication);
    return ResponseEntity.ok(ApiResponse.success(service.upload(kind, colourMode, file)));
  }

  @DeleteMapping("/{kind}")
  @Operation(
      operationId = "removeTenantBrandAsset",
      summary = "Remove a tenant brand asset (administrators only)")
  public ResponseEntity<ApiResponse<Void>> remove(
      @PathVariable BrandAssetKind kind, Authentication authentication) {
    requireAdministrator(authentication);
    service.remove(kind);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @GetMapping(value = "/assets/{id}", produces = "image/svg+xml")
  @Operation(
      operationId = "readTenantBrandAssetContent",
      summary = "Read a validated SVG belonging to the current tenant",
      responses =
          @io.swagger.v3.oas.annotations.responses.ApiResponse(
              responseCode = "200",
              description =
                  "Static SVG; monochrome artwork is recoloured for the requested appearance",
              content =
                  @Content(
                      mediaType = "image/svg+xml",
                      schema = @Schema(type = "string", format = "binary"))))
  public ResponseEntity<byte[]> content(
      @PathVariable UUID id, @RequestParam(defaultValue = "ORIGINAL") BrandAppearance appearance) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("image/svg+xml"))
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "default-src 'none'; style-src 'none'; sandbox")
        .body(service.content(id, appearance));
  }

  private void requireAdministrator(Authentication authentication) {
    if (!auth.can(authentication, "members", "manage")) {
      throw new AccessDeniedException("Only administrators can change tenant branding.");
    }
  }
}
