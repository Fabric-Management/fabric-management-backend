package com.fabricmanagement.finance.collection.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.finance.collection.app.CollectionChequeHandoverQueryService;
import com.fabricmanagement.finance.collection.app.CollectionChequeHandoverService;
import com.fabricmanagement.finance.collection.app.CollectionChequePreviewService;
import com.fabricmanagement.finance.collection.dto.ChequeHandoverDtos;
import com.fabricmanagement.finance.collection.dto.CollectionDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/collections/cheque-handovers")
@RequiredArgsConstructor
@Tag(name = "Finance Collections")
public class CollectionChequeHandoverController {
  private final CollectionChequePreviewService previews;
  private final CollectionChequeHandoverService handovers;
  private final CollectionChequeHandoverQueryService queries;

  @PostMapping("/preview")
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  @Operation(
      operationId = "previewFinanceCollectionChequeHandover",
      summary = "Propose cheque applications and compare maturity without posting or refreshing")
  public ResponseEntity<ApiResponse<ChequeHandoverDtos.Preview>> preview(
      @Valid @RequestBody ChequeHandoverDtos.PreviewRequest request, Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(previews.preview(request, actor(auth))));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  @Operation(
      operationId = "saveFinanceCollectionChequeHandover",
      summary = "Record a numbered cheque handover atomically with replay protection")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      description = "Invalid handover or cheque row",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ChequeHandoverDtos.SaveProblem.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description = "Stale instalments, replay conflict or rejected cheque row",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ChequeHandoverDtos.SaveProblem.class)))
  public ResponseEntity<ApiResponse<CollectionDtos.Result>> save(
      @Valid @RequestBody ChequeHandoverDtos.SaveRequest request, Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(handovers.save(request, actor(auth))));
  }

  @PostMapping("/target-refresh")
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  @Operation(
      operationId = "refreshFinanceCollectionChequeHandoverTargets",
      summary =
          "Refresh authorised targets from a stale handover without waiting for another command")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      description = "At least one target is missing or outside write scope")
  public ResponseEntity<ApiResponse<List<ChequeHandoverDtos.TargetRefresh>>> refreshTargets(
      @Valid @RequestBody ChequeHandoverDtos.RefreshRequest request, Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(handovers.refreshTargets(request, actor(auth))));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(
      operationId = "listFinanceCollectionChequeHandovers",
      summary = "Find readable cheque handovers, newest first",
      description = "The optional number filter matches a full handover number, ignoring case.")
  public ResponseEntity<ApiResponse<Page<ChequeHandoverDtos.HandoverSummary>>> list(
      @RequestParam(required = false) UUID partnerId,
      @RequestParam(required = false) String number,
      @Parameter(
              description = "Zero-based page index",
              schema =
                  @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(
              description = "Page size, capped at 100",
              schema =
                  @Schema(
                      type = "integer",
                      format = "int32",
                      minimum = "1",
                      maximum = "100",
                      defaultValue = "20"))
          @RequestParam(defaultValue = "20")
          int size,
      Authentication auth) {
    var paging = PageRequest.of(Math.max(0, page), Math.max(1, size));
    return ResponseEntity.ok(
        ApiResponse.success(queries.list(actor(auth), partnerId, number, paging)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(
      operationId = "getFinanceCollectionChequeHandover",
      summary = "Read a handover's visible cheques in receipt order with original receipt totals")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      description = "The handover is absent or none of its cheques are readable")
  public ResponseEntity<ApiResponse<ChequeHandoverDtos.HandoverDetail>> detail(
      @PathVariable UUID id, Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(queries.detail(id, actor(auth))));
  }

  private static UUID actor(Authentication auth) {
    if (auth != null
        && auth.getPrincipal() instanceof AuthenticatedUserContext user
        && user.userId() != null) return user.userId();
    throw new AccessDeniedException("A signed-in finance user is required");
  }
}
