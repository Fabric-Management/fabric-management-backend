package com.fabricmanagement.product.fabric.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.product.fabric.app.FabricArticleService;
import com.fabricmanagement.product.fabric.app.FabricCompositionSuggestionService;
import com.fabricmanagement.product.fabric.app.FabricConstructionCatalogService;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricArticleStatus;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import com.fabricmanagement.product.fabric.dto.CreateFabricConstructionRequest;
import com.fabricmanagement.product.fabric.dto.FabricArticleDto;
import com.fabricmanagement.product.fabric.dto.FabricArticleListItemDto;
import com.fabricmanagement.product.fabric.dto.FabricArticleRequest;
import com.fabricmanagement.product.fabric.dto.FabricCompositionSuggestionDto;
import com.fabricmanagement.product.fabric.dto.FabricCompositionSuggestionRequest;
import com.fabricmanagement.product.fabric.dto.FabricConstructionDto;
import com.fabricmanagement.product.fabric.dto.UpdateFabricConstructionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production/fabrics")
@RequiredArgsConstructor
@Validated
@Tag(name = "Fabric Articles", description = "Fabric master data and construction dictionary")
public class FabricArticleController {

  private final FabricArticleService articles;
  private final FabricConstructionCatalogService constructions;
  private final FabricCompositionSuggestionService suggestions;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'fabric', 'read')")
  @Operation(operationId = "listFabricArticles", summary = "List and search fabric articles")
  public ResponseEntity<ApiResponse<PagedResponse<FabricArticleListItemDto>>> list(
      @RequestParam(name = "status", required = false) FabricArticleStatus status,
      @Parameter(description = "Case-insensitive part of the name or article code")
          @RequestParam(name = "q", required = false)
          String q,
      @ParameterObject
          @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(articles.list(status, q, pageable))));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'fabric', 'read')")
  @Operation(operationId = "getFabricArticle", summary = "Get a fabric article")
  public ResponseEntity<ApiResponse<FabricArticleDto>> get(@PathVariable("id") UUID id) {
    return ResponseEntity.ok(ApiResponse.success(articles.get(id)));
  }

  @GetMapping("/product/{productId}")
  @PreAuthorize("@auth.can(authentication, 'fabric', 'read')")
  @Operation(operationId = "getFabricArticleByProduct", summary = "Get a fabric by its product")
  public ResponseEntity<ApiResponse<FabricArticleDto>> getByProduct(
      @PathVariable("productId") UUID productId) {
    return ResponseEntity.ok(ApiResponse.success(articles.getByProduct(productId)));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'fabric', 'write')")
  @Operation(
      operationId = "createFabricArticle",
      summary = "Create a draft fabric article and its product")
  public ResponseEntity<ApiResponse<FabricArticleDto>> create(
      @Valid @RequestBody FabricArticleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(articles.create(request)));
  }

  @PostMapping("/composition-suggestion")
  @PreAuthorize("@auth.can(authentication, 'fabric', 'read')")
  @Operation(
      operationId = "suggestFabricComposition",
      summary = "Work out a fabric composition from its yarns",
      description =
          "Each yarn's share of the mass times its fibre percentages, rounded to add up to 100."
              + " Nothing is saved.")
  public ResponseEntity<ApiResponse<FabricCompositionSuggestionDto>> suggestComposition(
      @Valid @RequestBody FabricCompositionSuggestionRequest request) {
    return ResponseEntity.ok(ApiResponse.success(suggestions.suggest(request.yarns())));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'fabric', 'write')")
  @Operation(operationId = "updateFabricArticle", summary = "Replace a fabric article")
  public ResponseEntity<ApiResponse<FabricArticleDto>> update(
      @PathVariable("id") UUID id, @Valid @RequestBody FabricArticleRequest request) {
    return ResponseEntity.ok(ApiResponse.success(articles.update(id, request)));
  }

  @PostMapping("/{id}/activate")
  @PreAuthorize("@auth.can(authentication, 'fabric', 'write')")
  @Operation(operationId = "activateFabricArticle", summary = "Activate a complete draft")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description = "The article is not complete; details.problems lists what is missing")
  public ResponseEntity<ApiResponse<FabricArticleDto>> activate(@PathVariable("id") UUID id) {
    return ResponseEntity.ok(ApiResponse.success(articles.activate(id)));
  }

  @PostMapping("/{id}/obsolete")
  @PreAuthorize("@auth.can(authentication, 'fabric', 'write')")
  @Operation(operationId = "obsoleteFabricArticle", summary = "Retire an active fabric article")
  public ResponseEntity<ApiResponse<FabricArticleDto>> obsolete(@PathVariable("id") UUID id) {
    return ResponseEntity.ok(ApiResponse.success(articles.obsolete(id)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'fabric', 'write')")
  @Operation(
      operationId = "deleteFabricArticle",
      summary = "Delete a draft fabric article; an active one is made obsolete instead")
  public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
    articles.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/constructions")
  @PreAuthorize("@auth.can(authentication, 'fabric', 'read')")
  @Operation(operationId = "listFabricConstructions", summary = "The construction dictionary")
  public ResponseEntity<ApiResponse<List<FabricConstructionDto>>> listConstructions(
      @RequestParam(name = "formation", required = false) FabricFormation formation,
      @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
    return ResponseEntity.ok(ApiResponse.success(constructions.list(formation, includeInactive)));
  }

  @PostMapping("/constructions")
  @PreAuthorize("@auth.can(authentication, 'fabric', 'write')")
  @Operation(operationId = "createFabricConstruction", summary = "Add a tenant construction")
  public ResponseEntity<ApiResponse<FabricConstructionDto>> createConstruction(
      @Valid @RequestBody CreateFabricConstructionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(constructions.create(request)));
  }

  @PutMapping("/constructions/{id}")
  @PreAuthorize("@auth.can(authentication, 'fabric', 'write')")
  @Operation(operationId = "updateFabricConstruction", summary = "Rename or retire a construction")
  public ResponseEntity<ApiResponse<FabricConstructionDto>> updateConstruction(
      @PathVariable("id") UUID id, @Valid @RequestBody UpdateFabricConstructionRequest request) {
    return ResponseEntity.ok(ApiResponse.success(constructions.update(id, request)));
  }
}
