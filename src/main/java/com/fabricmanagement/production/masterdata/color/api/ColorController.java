package com.fabricmanagement.production.masterdata.color.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.masterdata.color.app.ColorService;
import com.fabricmanagement.production.masterdata.color.dto.ColorDto;
import com.fabricmanagement.production.masterdata.color.dto.CreateColorRequest;
import com.fabricmanagement.production.masterdata.color.dto.UpdateColorRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  @Operation(operationId = "listColors", summary = "List color cards")
  public ResponseEntity<ApiResponse<List<ColorDto>>> list(
      @RequestParam(defaultValue = "false") boolean includeInactive) {
    return ResponseEntity.ok(
        ApiResponse.success(
            colorService.list(includeInactive).stream().map(ColorDto::from).toList()));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  @Operation(operationId = "findColorById", summary = "Get a color card by ID")
  public ResponseEntity<ApiResponse<ColorDto>> findById(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(ColorDto.from(colorService.findById(id))));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(operationId = "createColor", summary = "Create a color card")
  public ResponseEntity<ApiResponse<ColorDto>> create(
      @Valid @RequestBody CreateColorRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(ColorDto.from(colorService.create(request.toSpec()))));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(operationId = "updateColor", summary = "Update a color card")
  public ResponseEntity<ApiResponse<ColorDto>> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateColorRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(ColorDto.from(colorService.update(id, request.toSpec()))));
  }

  @PostMapping("/{id}/deactivate")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(operationId = "deactivateColor", summary = "Deactivate a color card")
  public ResponseEntity<ApiResponse<ColorDto>> deactivate(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(ColorDto.from(colorService.deactivate(id))));
  }

  @PostMapping("/{id}/activate")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(operationId = "activateColor", summary = "Reactivate a deactivated color card")
  public ResponseEntity<ApiResponse<ColorDto>> activate(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(ColorDto.from(colorService.activate(id))));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(
      operationId = "approveColorStandard",
      summary = "Approve the card's shade standard, freezing its standard-defining fields")
  public ResponseEntity<ApiResponse<ColorDto>> approve(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(ColorDto.from(colorService.approve(id))));
  }

  @PostMapping("/{id}/revert-to-draft")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(
      operationId = "revertColorStandardToDraft",
      summary = "Reopen the card's shade standard for editing")
  public ResponseEntity<ApiResponse<ColorDto>> revertToDraft(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(ColorDto.from(colorService.revertToDraft(id))));
  }
}
