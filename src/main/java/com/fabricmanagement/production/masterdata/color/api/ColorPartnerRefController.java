package com.fabricmanagement.production.masterdata.color.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.production.masterdata.color.app.ColorPartnerRefQueryService;
import com.fabricmanagement.production.masterdata.color.app.ColorPartnerRefService;
import com.fabricmanagement.production.masterdata.color.domain.PartnerRole;
import com.fabricmanagement.production.masterdata.color.dto.AddColorPartnerCodeRequest;
import com.fabricmanagement.production.masterdata.color.dto.ColorPartnerCodeDto;
import com.fabricmanagement.production.masterdata.color.dto.ColorPartnerForwardResolutionDto;
import com.fabricmanagement.production.masterdata.color.dto.ColorPartnerRefDto;
import com.fabricmanagement.production.masterdata.color.dto.ColorPartnerReverseResolutionDto;
import com.fabricmanagement.production.masterdata.color.dto.CreateColorPartnerRefRequest;
import com.fabricmanagement.production.masterdata.color.dto.ReactivateColorPartnerRefRequest;
import com.fabricmanagement.production.masterdata.color.dto.UpdateColorPartnerCodeRequest;
import com.fabricmanagement.production.masterdata.color.dto.UpdateColorPartnerRefRequest;
import com.fabricmanagement.production.masterdata.color.mapper.ColorPartnerRefMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production")
@RequiredArgsConstructor
@Validated
@Tag(name = "Color Partner Reference", description = "Partner-specific color codes and aliases")
public class ColorPartnerRefController {

  private final ColorPartnerRefService colorPartnerRefService;
  private final ColorPartnerRefQueryService colorPartnerRefQueryService;
  private final ColorPartnerRefMapper colorPartnerRefMapper;

  @GetMapping("/colors/{colorId}/partner-refs")
  @PreAuthorize("@auth.can(authentication, 'colors', 'read')")
  @Operation(
      operationId = "listColorPartnerRefs",
      summary = "List a color card's partner references")
  public ResponseEntity<ApiResponse<PagedResponse<ColorPartnerRefDto>>> list(
      @Parameter(description = "Tenant-owned color identifier") @PathVariable UUID colorId,
      @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(
                colorPartnerRefQueryService.list(colorId, pageable),
                colorPartnerRefMapper::toDto)));
  }

  @PostMapping("/colors/{colorId}/partner-refs")
  @PreAuthorize("@auth.can(authentication, 'colors', 'write')")
  @Operation(
      operationId = "createColorPartnerRef",
      summary = "Create a partner reference and initial primary code atomically")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "201",
      description = "Color partner reference created successfully")
  public ResponseEntity<ApiResponse<ColorPartnerRefDto>> create(
      @Parameter(description = "Tenant-owned color identifier") @PathVariable UUID colorId,
      @Valid @RequestBody CreateColorPartnerRefRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                colorPartnerRefMapper.toDto(colorPartnerRefService.create(colorId, request))));
  }

  @PutMapping("/colors/{colorId}/partner-refs/{refId}")
  @PreAuthorize("@auth.can(authentication, 'colors', 'write')")
  @Operation(
      operationId = "updateColorPartnerRef",
      summary = "Replace a partner reference's mutable state")
  public ResponseEntity<ApiResponse<ColorPartnerRefDto>> update(
      @Parameter(description = "Tenant-owned color identifier") @PathVariable UUID colorId,
      @Parameter(description = "Color partner-reference identifier") @PathVariable UUID refId,
      @Valid @RequestBody UpdateColorPartnerRefRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            colorPartnerRefMapper.toDto(colorPartnerRefService.update(colorId, refId, request))));
  }

  @PostMapping("/colors/{colorId}/partner-refs/{refId}/codes")
  @PreAuthorize("@auth.can(authentication, 'colors', 'write')")
  @Operation(operationId = "addColorPartnerCode", summary = "Add an alias code")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "201",
      description = "Color partner alias created successfully")
  public ResponseEntity<ApiResponse<ColorPartnerCodeDto>> addCode(
      @Parameter(description = "Tenant-owned color identifier") @PathVariable UUID colorId,
      @Parameter(description = "Color partner-reference identifier") @PathVariable UUID refId,
      @Valid @RequestBody AddColorPartnerCodeRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                colorPartnerRefMapper.toDto(
                    colorPartnerRefService.addCode(colorId, refId, request))));
  }

  @PutMapping("/colors/{colorId}/partner-refs/{refId}/codes/{codeId}")
  @PreAuthorize("@auth.can(authentication, 'colors', 'write')")
  @Operation(operationId = "updateColorPartnerCode", summary = "Replace an alias's mutable name")
  public ResponseEntity<ApiResponse<ColorPartnerCodeDto>> updateCode(
      @Parameter(description = "Tenant-owned color identifier") @PathVariable UUID colorId,
      @Parameter(description = "Color partner-reference identifier") @PathVariable UUID refId,
      @Parameter(description = "Partner code identifier") @PathVariable UUID codeId,
      @Valid @RequestBody UpdateColorPartnerCodeRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            colorPartnerRefMapper.toDto(
                colorPartnerRefService.updateCode(colorId, refId, codeId, request))));
  }

  @PostMapping("/colors/{colorId}/partner-refs/{refId}/codes/{codeId}/make-primary")
  @PreAuthorize("@auth.can(authentication, 'colors', 'write')")
  @Operation(
      operationId = "makeColorPartnerCodePrimary",
      summary = "Switch the active primary code")
  public ResponseEntity<ApiResponse<ColorPartnerRefDto>> makePrimary(
      @Parameter(description = "Tenant-owned color identifier") @PathVariable UUID colorId,
      @Parameter(description = "Color partner-reference identifier") @PathVariable UUID refId,
      @Parameter(description = "Target active partner code identifier") @PathVariable UUID codeId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            colorPartnerRefMapper.toDto(
                colorPartnerRefService.makePrimary(colorId, refId, codeId))));
  }

  @PostMapping("/colors/{colorId}/partner-refs/{refId}/codes/{codeId}/deactivate")
  @PreAuthorize("@auth.can(authentication, 'colors', 'write')")
  @Operation(operationId = "deactivateColorPartnerCode", summary = "Deactivate a non-primary alias")
  public ResponseEntity<ApiResponse<ColorPartnerRefDto>> deactivateCode(
      @Parameter(description = "Tenant-owned color identifier") @PathVariable UUID colorId,
      @Parameter(description = "Color partner-reference identifier") @PathVariable UUID refId,
      @Parameter(description = "Partner code identifier") @PathVariable UUID codeId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            colorPartnerRefMapper.toDto(
                colorPartnerRefService.deactivateCode(colorId, refId, codeId))));
  }

  @PostMapping("/colors/{colorId}/partner-refs/{refId}/deactivate")
  @PreAuthorize("@auth.can(authentication, 'colors', 'write')")
  @Operation(
      operationId = "deactivateColorPartnerRef",
      summary = "Deactivate a relationship and all its codes")
  public ResponseEntity<ApiResponse<ColorPartnerRefDto>> deactivate(
      @Parameter(description = "Tenant-owned color identifier") @PathVariable UUID colorId,
      @Parameter(description = "Color partner-reference identifier") @PathVariable UUID refId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            colorPartnerRefMapper.toDto(colorPartnerRefService.deactivate(colorId, refId))));
  }

  @PostMapping("/colors/{colorId}/partner-refs/{refId}/reactivate")
  @PreAuthorize("@auth.can(authentication, 'colors', 'write')")
  @Operation(
      operationId = "reactivateColorPartnerRef",
      summary = "Reactivate with one available primary code")
  public ResponseEntity<ApiResponse<ColorPartnerRefDto>> reactivate(
      @Parameter(description = "Tenant-owned color identifier") @PathVariable UUID colorId,
      @Parameter(description = "Color partner-reference identifier") @PathVariable UUID refId,
      @Valid @RequestBody ReactivateColorPartnerRefRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            colorPartnerRefMapper.toDto(
                colorPartnerRefService.reactivate(colorId, refId, request))));
  }

  @GetMapping("/color-partner-refs/resolve")
  @PreAuthorize("@auth.can(authentication, 'colors', 'read')")
  @Operation(
      operationId = "resolveColorPartnerCode",
      summary = "Resolve any active partner alias to a color")
  public ResponseEntity<ApiResponse<ColorPartnerReverseResolutionDto>> resolve(
      @Parameter(description = "Trading partner identifier") @RequestParam @NotNull UUID partnerId,
      @Parameter(description = "Business direction of the mapping") @RequestParam @NotNull
          PartnerRole role,
      @Parameter(description = "Partner code in any case") @RequestParam @NotBlank @Size(max = 50)
          String externalCode) {
    return ResponseEntity.ok(
        ApiResponse.success(colorPartnerRefQueryService.resolve(partnerId, role, externalCode)));
  }

  @GetMapping("/color-partner-refs/forward")
  @PreAuthorize("@auth.can(authentication, 'colors', 'read')")
  @Operation(
      operationId = "findPrimaryColorPartnerCode",
      summary = "Find the active primary partner code for a color")
  public ResponseEntity<ApiResponse<ColorPartnerForwardResolutionDto>> forward(
      @Parameter(description = "Tenant-owned color identifier") @RequestParam @NotNull UUID colorId,
      @Parameter(description = "Trading partner identifier") @RequestParam @NotNull UUID partnerId,
      @Parameter(description = "Business direction of the mapping") @RequestParam @NotNull
          PartnerRole role) {
    return ResponseEntity.ok(
        ApiResponse.success(colorPartnerRefQueryService.forward(colorId, partnerId, role)));
  }
}
