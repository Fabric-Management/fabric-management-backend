package com.fabricmanagement.production.quality.result.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail;
import com.fabricmanagement.product.fiber.app.FiberTestStandardCatalogService;
import com.fabricmanagement.product.fiber.dto.CreateFiberTestStandardRequest;
import com.fabricmanagement.product.fiber.dto.FiberTestStandardDto;
import com.fabricmanagement.product.fiber.dto.RenameFiberTestStandardRequest;
import com.fabricmanagement.production.quality.result.app.FiberTestLaboratoryService;
import com.fabricmanagement.production.quality.result.dto.FiberTestLaboratoryDto;
import com.fabricmanagement.production.quality.result.dto.RegisterFiberTestLaboratoryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/production/quality/fiber-tests")
@RequiredArgsConstructor
@Validated
@Tag(
    name = "Fiber Test Catalogue",
    description = "External laboratories and standards for fibre tests")
public class FiberTestCatalogueController {

  private final FiberTestStandardCatalogService standards;
  private final FiberTestLaboratoryService laboratories;

  @GetMapping("/standards")
  @PreAuthorize(
      "@auth.can(authentication, 'quality', 'write') or @auth.can(authentication, 'quality', 'manage')")
  @Operation(
      operationId = "listFiberTestStandards",
      summary = "Search active fibre test standards by code or name",
      description = "Requires quality:write or quality:manage.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Page of active test standards",
        useReturnTypeSchema = true),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "The request could not be parsed",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Authentication is required",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "The required quality permission is missing",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "422",
        description = "The request fields fail validation",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class)))
  })
  public ResponseEntity<ApiResponse<PagedResponse<FiberTestStandardDto>>> listStandards(
      @RequestParam(name = "search", defaultValue = "") @Size(max = 255) String search,
      @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(200) int size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(standards.search(search, PageRequest.of(page, size)))));
  }

  @PostMapping("/standards")
  @PreAuthorize("@auth.can(authentication, 'quality', 'manage')")
  @Operation(
      operationId = "createFiberTestStandard",
      summary = "Add a fibre test standard or a new revision",
      description = "Requires quality:manage. Code and revision identify one entry per company.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Standard created",
        useReturnTypeSchema = true),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "The request could not be parsed",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Authentication is required",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "The required quality permission is missing",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "The code and revision already exist (FIBER_TEST_STANDARD_DUPLICATE)",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "422",
        description = "The request fields fail validation",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class)))
  })
  public ResponseEntity<ApiResponse<FiberTestStandardDto>> createStandard(
      @Valid @RequestBody CreateFiberTestStandardRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                standards.create(request.code(), request.name(), request.revision())));
  }

  @PatchMapping("/standards/{id}")
  @PreAuthorize("@auth.can(authentication, 'quality', 'manage')")
  @Operation(
      operationId = "renameFiberTestStandard",
      summary = "Update the display name of an active fibre test standard",
      description = "Requires quality:manage. Code and revision are immutable.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Standard renamed",
        useReturnTypeSchema = true),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "The request could not be parsed",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Authentication is required",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "The required quality permission is missing",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "The selection is unavailable in this company",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "The catalogue entry conflicts with an existing entry or has changed",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "422",
        description = "The request fields fail validation",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class)))
  })
  public ResponseEntity<ApiResponse<FiberTestStandardDto>> renameStandard(
      @PathVariable UUID id, @Valid @RequestBody RenameFiberTestStandardRequest request) {
    return ResponseEntity.ok(ApiResponse.success(standards.rename(id, request.name())));
  }

  @PostMapping("/standards/{id}/deactivate")
  @PreAuthorize("@auth.can(authentication, 'quality', 'manage')")
  @Operation(
      operationId = "deactivateFiberTestStandard",
      summary = "Retire a fibre test standard from new selections",
      description = "Requires quality:manage. Existing results retain the recorded standard.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "Standard inactive"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "The request could not be parsed",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Authentication is required",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "The required quality permission is missing",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "The selection is unavailable in this company",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "The catalogue entry conflicts with an existing entry or has changed",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class)))
  })
  public ResponseEntity<Void> deactivateStandard(@PathVariable UUID id) {
    standards.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/laboratories")
  @PreAuthorize(
      "@auth.can(authentication, 'quality', 'write') or @auth.can(authentication, 'quality', 'manage')")
  @Operation(
      operationId = "listFiberTestLaboratories",
      summary = "Search active external laboratories by trading partner name",
      description = "Requires quality:write or quality:manage.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Page of eligible external laboratories",
        useReturnTypeSchema = true),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "The request could not be parsed",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Authentication is required",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "The required quality permission is missing",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "422",
        description = "The request fields fail validation",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class)))
  })
  public ResponseEntity<ApiResponse<PagedResponse<FiberTestLaboratoryDto>>> listLaboratories(
      @RequestParam(name = "search", defaultValue = "") @Size(max = 255) String search,
      @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(200) int size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(laboratories.search(search, PageRequest.of(page, size)))));
  }

  @PostMapping("/laboratories")
  @PreAuthorize("@auth.can(authentication, 'quality', 'manage')")
  @Operation(
      operationId = "registerFiberTestLaboratory",
      summary = "Enable an existing trading partner as an external laboratory",
      description = "Requires quality:manage. An inactive registration is re-enabled.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "External laboratory enabled",
        useReturnTypeSchema = true),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "The request could not be parsed",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Authentication is required",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "The required quality permission is missing",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "The selection is unavailable in this company",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "The catalogue entry conflicts with an existing entry or has changed",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "422",
        description = "The request fields fail validation",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class)))
  })
  public ResponseEntity<ApiResponse<FiberTestLaboratoryDto>> registerLaboratory(
      @Valid @RequestBody RegisterFiberTestLaboratoryRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(laboratories.register(request.tradingPartnerId())));
  }

  @PostMapping("/laboratories/{id}/deactivate")
  @PreAuthorize("@auth.can(authentication, 'quality', 'manage')")
  @Operation(
      operationId = "deactivateFiberTestLaboratory",
      summary = "Remove a trading partner from new laboratory selections",
      description = "Requires quality:manage. The identifier is the trading partner ID.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "External laboratory inactive"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "The request could not be parsed",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Authentication is required",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "The required quality permission is missing",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "The selection is unavailable in this company",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "The catalogue entry conflicts with an existing entry or has changed",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class)))
  })
  public ResponseEntity<Void> deactivateLaboratory(@PathVariable UUID id) {
    laboratories.deactivate(id);
    return ResponseEntity.noContent().build();
  }
}
