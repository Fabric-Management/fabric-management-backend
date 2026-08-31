package com.fabricmanagement.product.yarn.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.product.yarn.app.EndUseCatalogService;
import com.fabricmanagement.product.yarn.app.SpinningSystemCatalogService;
import com.fabricmanagement.product.yarn.app.TestMethodCatalogService;
import com.fabricmanagement.product.yarn.app.YarnArticleService;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import com.fabricmanagement.product.yarn.dto.CreateYarnArticleRequest;
import com.fabricmanagement.product.yarn.dto.CreateYarnEndUseRequest;
import com.fabricmanagement.product.yarn.dto.CreateYarnSpinningSystemRequest;
import com.fabricmanagement.product.yarn.dto.CreateYarnTestMethodRequest;
import com.fabricmanagement.product.yarn.dto.UpdateYarnArticleMetadataRequest;
import com.fabricmanagement.product.yarn.dto.UpdateYarnCatalogueRequest;
import com.fabricmanagement.product.yarn.dto.YarnArticleDto;
import com.fabricmanagement.product.yarn.dto.YarnArticleHistoryDto;
import com.fabricmanagement.product.yarn.dto.YarnArticleHistorySnapshotDto;
import com.fabricmanagement.product.yarn.dto.YarnArticleListItemDto;
import com.fabricmanagement.product.yarn.dto.YarnArticleMutationResponse;
import com.fabricmanagement.product.yarn.dto.YarnArticleSpecRequest;
import com.fabricmanagement.product.yarn.dto.YarnCatalogueDto;
import com.fabricmanagement.product.yarn.dto.YarnInvariantProblemDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production/yarns")
@RequiredArgsConstructor
@Validated
@Tag(name = "Yarn Articles", description = "Yarn article lifecycle and tenant catalogues")
public class YarnArticleController {

  private final YarnArticleService articleService;
  private final SpinningSystemCatalogService spinningSystemService;
  private final EndUseCatalogService endUseService;
  private final TestMethodCatalogService testMethodService;

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "createYarnArticle", summary = "Create a draft yarn article")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "201",
      description = "Draft created")
  public ResponseEntity<ApiResponse<YarnArticleMutationResponse>> create(
      @Valid @RequestBody CreateYarnArticleRequest request) {
    YarnArticleMutationResponse response =
        articleService.createDraftResponse(
            request.productId(),
            request.name(),
            request.description(),
            request.spec() == null ? null : request.spec().toCommand());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'read')")
  @Operation(operationId = "getYarnArticle", summary = "Get a yarn article")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Found")
  public ResponseEntity<ApiResponse<YarnArticleDto>> get(@PathVariable("id") UUID id) {
    return ResponseEntity.ok(ApiResponse.success(articleService.getViewById(id)));
  }

  @GetMapping("/product/{productId}")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'read')")
  @Operation(operationId = "getYarnArticleByProduct", summary = "Get a yarn article by product")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Found")
  public ResponseEntity<ApiResponse<YarnArticleDto>> getByProduct(
      @PathVariable("productId") UUID productId) {
    return ResponseEntity.ok(ApiResponse.success(articleService.getViewByProductId(productId)));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'yarn', 'read')")
  @Operation(operationId = "listYarnArticles", summary = "List and filter yarn articles")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page")
  public ResponseEntity<ApiResponse<PagedResponse<YarnArticleListItemDto>>> list(
      @RequestParam(name = "status", required = false) YarnArticleStatus status,
      @Parameter(description = "Literal case-insensitive substring")
          @RequestParam(name = "q", required = false)
          String q,
      @RequestParam(name = "texMin", required = false) BigDecimal texMin,
      @RequestParam(name = "texMax", required = false) BigDecimal texMax,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(articleService.list(status, q, texMin, texMax, pageable))));
  }

  @PutMapping("/{id}/spec")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "updateYarnArticleSpec", summary = "Replace a yarn article spec")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Updated")
  public ResponseEntity<ApiResponse<YarnArticleMutationResponse>> updateSpec(
      @PathVariable("id") UUID id, @Valid @RequestBody YarnArticleSpecRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(articleService.updateSpecResponse(id, request.toCommand())));
  }

  @PatchMapping("/{id}/metadata")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "updateYarnArticleMetadata", summary = "Update article metadata")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Updated")
  public ResponseEntity<ApiResponse<YarnArticleDto>> updateMetadata(
      @PathVariable("id") UUID id, @Valid @RequestBody UpdateYarnArticleMetadataRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            articleService.updateMetadataView(id, request.name(), request.description())));
  }

  @PostMapping("/{id}/activate")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "activateYarnArticle", summary = "Activate a valid draft")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Activated")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description = "One or more yarn invariants were violated",
      content = @Content(schema = @Schema(implementation = YarnInvariantProblemDetail.class)))
  public ResponseEntity<ApiResponse<YarnArticleDto>> activate(@PathVariable("id") UUID id) {
    return ResponseEntity.ok(ApiResponse.success(articleService.activateView(id)));
  }

  @PostMapping("/{id}/obsolete")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "obsoleteYarnArticle", summary = "Mark an active article obsolete")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Obsolete")
  public ResponseEntity<ApiResponse<YarnArticleDto>> obsolete(@PathVariable("id") UUID id) {
    return ResponseEntity.ok(ApiResponse.success(articleService.markObsoleteView(id)));
  }

  @GetMapping("/{id}/history")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'read')")
  @Operation(operationId = "listYarnArticleHistory", summary = "List yarn article audit events")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page")
  public ResponseEntity<ApiResponse<PagedResponse<YarnArticleHistoryDto>>> history(
      @PathVariable("id") UUID id,
      @ParameterObject
          @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.ASC)
          Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(articleService.history(id, pageable))));
  }

  @GetMapping("/{id}/history/{version}")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'read')")
  @Operation(operationId = "getYarnArticleHistoryVersion", summary = "Get one spec snapshot")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Found")
  public ResponseEntity<ApiResponse<YarnArticleHistorySnapshotDto>> historyVersion(
      @PathVariable("id") UUID id, @PathVariable("version") @Min(1) int version) {
    return ResponseEntity.ok(ApiResponse.success(articleService.historyVersion(id, version)));
  }

  @GetMapping("/spinning-systems")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'read')")
  @Operation(operationId = "listYarnSpinningSystems", summary = "List spinning systems")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page")
  public ResponseEntity<ApiResponse<PagedResponse<YarnCatalogueDto>>> spinningSystems(
      @ParameterObject
          @PageableDefault(
              size = 100,
              sort = {"displayOrder", "code"})
          Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(spinningSystemService.list(pageable), YarnCatalogueDto::from)));
  }

  @PostMapping("/spinning-systems")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "createYarnSpinningSystem", summary = "Create a spinning system")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "201",
      description = "Created")
  public ResponseEntity<ApiResponse<YarnCatalogueDto>> createSpinningSystem(
      @Valid @RequestBody CreateYarnSpinningSystemRequest request) {
    YarnCatalogueDto created =
        YarnCatalogueDto.from(
            spinningSystemService.defineTenantSpinningSystem(
                request.code(),
                request.name(),
                request.description(),
                request.displayOrder(),
                request.technologyFamily()));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
  }

  @PatchMapping("/spinning-systems/{id}")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(
      operationId = "updateYarnSpinningSystem",
      summary = "Update mutable presentation fields")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Updated")
  public ResponseEntity<ApiResponse<YarnCatalogueDto>> updateSpinningSystem(
      @PathVariable("id") UUID id, @Valid @RequestBody UpdateYarnCatalogueRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            YarnCatalogueDto.from(
                spinningSystemService.updateMutable(
                    id, request.name(), request.description(), request.displayOrder()))));
  }

  @PostMapping("/spinning-systems/{id}/deactivate")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "deactivateYarnSpinningSystem", summary = "Deactivate a spinning system")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Deactivated")
  public ResponseEntity<ApiResponse<YarnCatalogueDto>> deactivateSpinningSystem(
      @PathVariable("id") UUID id) {
    return ResponseEntity.ok(
        ApiResponse.success(YarnCatalogueDto.from(spinningSystemService.deactivate(id))));
  }

  @GetMapping("/end-uses")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'read')")
  @Operation(operationId = "listYarnEndUses", summary = "List yarn end uses")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page")
  public ResponseEntity<ApiResponse<PagedResponse<YarnCatalogueDto>>> endUses(
      @ParameterObject
          @PageableDefault(
              size = 100,
              sort = {"displayOrder", "code"})
          Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(endUseService.list(pageable), YarnCatalogueDto::from)));
  }

  @PostMapping("/end-uses")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "createYarnEndUse", summary = "Create a yarn end use")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "201",
      description = "Created")
  public ResponseEntity<ApiResponse<YarnCatalogueDto>> createEndUse(
      @Valid @RequestBody CreateYarnEndUseRequest request) {
    YarnCatalogueDto created =
        YarnCatalogueDto.from(
            endUseService.defineTenantEndUse(
                request.code(), request.name(), request.description(), request.displayOrder()));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
  }

  @PatchMapping("/end-uses/{id}")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "updateYarnEndUse", summary = "Update mutable presentation fields")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Updated")
  public ResponseEntity<ApiResponse<YarnCatalogueDto>> updateEndUse(
      @PathVariable("id") UUID id, @Valid @RequestBody UpdateYarnCatalogueRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            YarnCatalogueDto.from(
                endUseService.updateMutable(
                    id, request.name(), request.description(), request.displayOrder()))));
  }

  @PostMapping("/end-uses/{id}/deactivate")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "deactivateYarnEndUse", summary = "Deactivate a yarn end use")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Deactivated")
  public ResponseEntity<ApiResponse<YarnCatalogueDto>> deactivateEndUse(
      @PathVariable("id") UUID id) {
    return ResponseEntity.ok(
        ApiResponse.success(YarnCatalogueDto.from(endUseService.deactivate(id))));
  }

  @GetMapping("/test-methods")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'read')")
  @Operation(operationId = "listYarnTestMethods", summary = "List yarn test methods")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page")
  public ResponseEntity<ApiResponse<PagedResponse<YarnCatalogueDto>>> testMethods(
      @ParameterObject
          @PageableDefault(
              size = 100,
              sort = {"displayOrder", "code"})
          Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(testMethodService.list(pageable), YarnCatalogueDto::from)));
  }

  @PostMapping("/test-methods")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "createYarnTestMethod", summary = "Create a yarn test method")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "201",
      description = "Created")
  public ResponseEntity<ApiResponse<YarnCatalogueDto>> createTestMethod(
      @Valid @RequestBody CreateYarnTestMethodRequest request) {
    YarnCatalogueDto created =
        YarnCatalogueDto.from(
            testMethodService.defineTenantTestMethod(
                request.code(),
                request.name(),
                request.description(),
                request.displayOrder(),
                request.standardRef(),
                request.instrument(),
                request.applicablePropertyKey()));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
  }

  @PatchMapping("/test-methods/{id}")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "updateYarnTestMethod", summary = "Update mutable presentation fields")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Updated")
  public ResponseEntity<ApiResponse<YarnCatalogueDto>> updateTestMethod(
      @PathVariable("id") UUID id, @Valid @RequestBody UpdateYarnCatalogueRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            YarnCatalogueDto.from(
                testMethodService.updateMutable(
                    id, request.name(), request.description(), request.displayOrder()))));
  }

  @PostMapping("/test-methods/{id}/deactivate")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "deactivateYarnTestMethod", summary = "Deactivate a yarn test method")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Deactivated")
  public ResponseEntity<ApiResponse<YarnCatalogueDto>> deactivateTestMethod(
      @PathVariable("id") UUID id) {
    return ResponseEntity.ok(
        ApiResponse.success(YarnCatalogueDto.from(testMethodService.deactivate(id))));
  }
}
