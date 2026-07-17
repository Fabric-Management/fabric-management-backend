package com.fabricmanagement.production.masterdata.color.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.production.masterdata.color.app.ColorService;
import com.fabricmanagement.production.masterdata.color.domain.ColorFamily;
import com.fabricmanagement.production.masterdata.color.domain.ColorStandardStatus;
import com.fabricmanagement.production.masterdata.color.domain.ColorType;
import com.fabricmanagement.production.masterdata.color.dto.ColorDto;
import com.fabricmanagement.production.masterdata.color.dto.CreateColorRequest;
import com.fabricmanagement.production.masterdata.color.dto.UpdateColorRequest;
import com.fabricmanagement.production.masterdata.color.mapper.ColorMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production/colors")
@RequiredArgsConstructor
@Tag(name = "Color", description = "Tenant color-card master data")
public class ColorController {

  private final ColorService colorService;
  private final ColorMapper colorMapper;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'colors', 'read')")
  @Operation(
      operationId = "listColors",
      summary = "List color cards",
      description = "Returns a tenant-scoped, filtered and paginated list of color cards.")
  public ResponseEntity<ApiResponse<PagedResponse<ColorDto>>> list(
      @Parameter(description = "Case-insensitive literal substring of the color code or name")
          @RequestParam(required = false)
          String q,
      @Parameter(description = "Optional color-process classification filter")
          @RequestParam(required = false)
          ColorType colorType,
      @Parameter(description = "Optional visual color-family filter")
          @RequestParam(required = false)
          ColorFamily colorFamily,
      @Parameter(description = "Optional shade-standard lifecycle filter")
          @RequestParam(required = false)
          ColorStandardStatus standardStatus,
      @Parameter(description = "Include soft-deleted color cards; defaults to false")
          @RequestParam(defaultValue = "false")
          boolean includeInactive,
      @ParameterObject @PageableDefault(size = 20, sort = "code") Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(
                colorService.list(
                    q, colorType, colorFamily, standardStatus, includeInactive, pageable),
                colorMapper::toDto)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'colors', 'read')")
  @Operation(operationId = "findColorById", summary = "Get a color card by ID")
  public ResponseEntity<ApiResponse<ColorDto>> findById(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(colorMapper.toDto(colorService.findById(id))));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'colors', 'write')")
  @Operation(operationId = "createColor", summary = "Create a color card")
  public ResponseEntity<ApiResponse<ColorDto>> create(
      @Valid @RequestBody CreateColorRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(colorMapper.toDto(colorService.create(request.toSpec()))));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'colors', 'write')")
  @Operation(
      operationId = "updateColor",
      summary = "Replace a color card's mutable state",
      description =
          "Full replacement: code and name are required; omitted nullable fields are cleared. "
              + "Omitted colorType and colorFamily resolve to DYED and UNDEFINED. "
              + "The standard lifecycle changes only through approve/revert endpoints.")
  public ResponseEntity<ApiResponse<ColorDto>> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateColorRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(colorMapper.toDto(colorService.update(id, request.toSpec()))));
  }

  @PostMapping("/{id}/deactivate")
  @PreAuthorize("@auth.can(authentication, 'colors', 'manage')")
  @Operation(operationId = "deactivateColor", summary = "Deactivate a color card")
  public ResponseEntity<ApiResponse<ColorDto>> deactivate(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(colorMapper.toDto(colorService.deactivate(id))));
  }

  @PostMapping("/{id}/activate")
  @PreAuthorize("@auth.can(authentication, 'colors', 'manage')")
  @Operation(operationId = "activateColor", summary = "Reactivate a deactivated color card")
  public ResponseEntity<ApiResponse<ColorDto>> activate(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(colorMapper.toDto(colorService.activate(id))));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("@auth.can(authentication, 'colors', 'approve')")
  @Operation(
      operationId = "approveColorStandard",
      summary = "Approve the card's shade standard, freezing its standard-defining fields")
  public ResponseEntity<ApiResponse<ColorDto>> approve(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(colorMapper.toDto(colorService.approve(id))));
  }

  @PostMapping("/{id}/revert-to-draft")
  @PreAuthorize("@auth.can(authentication, 'colors', 'approve')")
  @Operation(
      operationId = "revertColorStandardToDraft",
      summary = "Reopen the card's shade standard for editing")
  public ResponseEntity<ApiResponse<ColorDto>> revertToDraft(@PathVariable UUID id) {
    return ResponseEntity.ok(
        ApiResponse.success(colorMapper.toDto(colorService.revertToDraft(id))));
  }
}
