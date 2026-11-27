package com.fabricmanagement.production.quality.result.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail;
import com.fabricmanagement.production.quality.result.app.FiberTestResultService;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.dto.CreateFiberTestResultRequest;
import com.fabricmanagement.production.quality.result.dto.FiberTestBaleChoiceDto;
import com.fabricmanagement.production.quality.result.dto.FiberTestResultDto;
import com.fabricmanagement.production.quality.result.dto.UpdateApprovalRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for fibre laboratory test results.
 *
 * <p>Recording a test and looking up bale choices require quality write access. Reading results
 * requires quality read access. Updating a result's approval requires quality approval access.
 */
@RestController
@RequestMapping("/api/v1/production/quality/fiber-tests")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Fiber Test Result", description = "Fiber Test Result operations")
public class FiberTestResultController {

  private final FiberTestResultService testResultService;

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'quality', 'write')")
  @Operation(
      operationId = "createTestResult",
      summary = "Record a fibre test result",
      description =
          "Requires quality:write. Optional catalogue selections are validated before recording.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Fibre test result recorded",
        useReturnTypeSchema = true),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "The request could not be parsed or mixes a selection with its legacy text",
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
        description = "Quality write access is required",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "The selected laboratory or standard is unavailable in this company",
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
  public ResponseEntity<ApiResponse<FiberTestResultDto>> createTestResult(
      @Valid @RequestBody CreateFiberTestResultRequest request) {
    log.info("Recording fiber test result: batchId={}", request.getBatchId());
    FiberTestResultDto result = testResultService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  public ResponseEntity<ApiResponse<FiberTestResultDto>> getTestResult(@PathVariable UUID id) {
    return ResponseEntity.ok(
        ApiResponse.success(
            testResultService
                .getById(id)
                .orElseThrow(
                    () -> new EntityNotFoundException("Fiber test result not found: " + id))));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  public ResponseEntity<ApiResponse<List<FiberTestResultDto>>> getAllTestResults() {
    return ResponseEntity.ok(ApiResponse.success(testResultService.getAll()));
  }

  @GetMapping("/batch/{batchId}")
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  public ResponseEntity<ApiResponse<List<FiberTestResultDto>>> getTestResultsByBatch(
      @PathVariable UUID batchId) {
    return ResponseEntity.ok(ApiResponse.success(testResultService.getByBatchId(batchId)));
  }

  @GetMapping("/batch/{batchId}/bales")
  @PreAuthorize("@auth.can(authentication, 'quality', 'write')")
  @Operation(
      operationId = "listFiberTestBaleChoices",
      summary = "List active bale choices for recording a fibre test",
      description = "Returns active stock units in bale-code order, regardless of status.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Page of active bale choices",
        useReturnTypeSchema = true),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "The batch identifier, page or size could not be parsed",
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
        description = "Quality write access is required",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "The lot is not visible in this tenant",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "422",
        description = "Page must be zero or greater and size must be between 1 and 200",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblemDetail.class)))
  })
  public ResponseEntity<ApiResponse<PagedResponse<FiberTestBaleChoiceDto>>> listBaleChoices(
      @PathVariable("batchId") UUID batchId,
      @Parameter(description = "Case-insensitive substring of the bale code")
          @RequestParam(name = "search", defaultValue = "")
          String search,
      @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(200) int size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(
                testResultService.listBaleChoices(batchId, search, PageRequest.of(page, size)))));
  }

  @GetMapping("/stock-unit/{stockUnitId}")
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  public ResponseEntity<ApiResponse<List<FiberTestResultDto>>> getTestResultsByStockUnit(
      @PathVariable UUID stockUnitId) {
    return ResponseEntity.ok(ApiResponse.success(testResultService.getByStockUnitId(stockUnitId)));
  }

  @GetMapping("/status/{status}")
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  public ResponseEntity<ApiResponse<List<FiberTestResultDto>>> getTestResultsByStatus(
      @PathVariable TestApprovalStatus status) {
    return ResponseEntity.ok(ApiResponse.success(testResultService.getByApprovalStatus(status)));
  }

  @PatchMapping("/{id}/approval")
  @PreAuthorize("@auth.can(authentication, 'quality', 'approve')")
  public ResponseEntity<ApiResponse<FiberTestResultDto>> updateApproval(
      @PathVariable UUID id, @Valid @RequestBody UpdateApprovalRequest request) {
    log.info("Updating test approval: id={}, status={}", id, request.getApprovalStatus());
    FiberTestResultDto result = testResultService.updateApproval(id, request);
    return ResponseEntity.ok(
        ApiResponse.success(result, "Test approval updated to " + request.getApprovalStatus()));
  }
}
