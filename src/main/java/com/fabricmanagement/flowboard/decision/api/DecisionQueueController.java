package com.fabricmanagement.flowboard.decision.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail;
import com.fabricmanagement.flowboard.decision.app.*;
import com.fabricmanagement.flowboard.decision.domain.DecisionQueueBucket;
import com.fabricmanagement.flowboard.decision.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flowboard/decisions")
@Validated
@Tag(name = "FlowBoard Decisions", description = "Scoped decision queue and projection repair")
public class DecisionQueueController {
  private final DecisionQueueService queue;
  private final DecisionProjectionRebuildService rebuild;

  public DecisionQueueController(
      DecisionQueueService queue, DecisionProjectionRebuildService rebuild) {
    this.queue = queue;
    this.rebuild = rebuild;
  }

  @GetMapping
  @PreAuthorize(
      "@auth.can(authentication, 'flowboard', 'read') and @auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "List the caller's scoped decision queue")
  public ApiResponse<PagedResponse<DecisionQueueItem>> list(
      @RequestParam(defaultValue = "MINE") DecisionQueueBucket bucket,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam Map<String, String> parameters,
      Authentication authentication) {
    if (!Set.of("bucket", "page", "size").containsAll(parameters.keySet())) {
      throw new IllegalArgumentException("Unsupported decision queue filter or sort");
    }
    return ApiResponse.success(
        queue.list(TenantContext.requireTenantId(), actor(authentication), bucket, page, size));
  }

  @GetMapping("/summary")
  @PreAuthorize(
      "@auth.can(authentication, 'flowboard', 'read') and @auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Count the caller's decision buckets")
  public ApiResponse<DecisionQueueSummary> summary(Authentication authentication) {
    return ApiResponse.success(
        queue.summary(TenantContext.requireTenantId(), actor(authentication)));
  }

  @PostMapping("/projection/rebuild")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'manage-routing')")
  @Operation(summary = "Rebuild selected or all order-cover decision projections")
  public ApiResponse<DecisionProjectionRebuildResponse> rebuild(
      @Valid @RequestBody(required = false) DecisionProjectionRebuildRequest request) {
    return ApiResponse.success(
        rebuild.rebuild(
            TenantContext.requireTenantId(),
            request == null ? java.util.List.of() : request.caseIds()));
  }

  /** Queue request bounds are a 400 contract; other endpoints retain the global 422 policy. */
  @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiProblemDetail invalidRequest(HttpServletRequest request) {
    ApiProblemDetail problem =
        ApiProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Decision queue request validation failed");
    problem.setTitle("Bad Request");
    problem.setCode("VALIDATION_ERROR");
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }

  private UUID actor(Authentication authentication) {
    if (authentication != null
        && authentication.getDetails() instanceof AuthenticatedUserContext context) {
      return context.userId();
    }
    throw new AccessDeniedException("Authenticated user context is required");
  }
}
