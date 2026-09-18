package com.fabricmanagement.flowboard.routing.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.flowboard.routing.app.*;
import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.flowboard.routing.domain.exception.RoutingMembersRejectedException;
import com.fabricmanagement.flowboard.routing.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flowboard/routing")
@RequiredArgsConstructor
@Tag(
    name = "FlowBoard Routing",
    description = "Governed pool configuration, eligibility and repair")
public class RoutingController {
  private final RoutingQueryService queries;
  private final RoutingPoolConfigurationService configuration;
  private final RoutingRepairService repair;

  @GetMapping("/pools/{poolKey}")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'manage-routing')")
  @Operation(summary = "Read a routing pool; unconfigured pools return configured=false")
  public ApiResponse<RoutingPoolResponse> pool(@PathVariable RoutingPoolKey poolKey) {
    return ApiResponse.success(queries.pool(TenantContext.requireTenantId(), poolKey));
  }

  @PutMapping("/pools/{poolKey}")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'manage-routing')")
  @Operation(summary = "Configure a routing pool with an expected revision")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description = "Revision conflict")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "422",
      description = "Non-candidate members, with per-user reasons")
  public ApiResponse<RoutingPoolResponse> configure(
      @PathVariable RoutingPoolKey poolKey, @Valid @RequestBody RoutingPoolUpdateRequest request) {
    UUID tenant = TenantContext.requireTenantId();
    configuration.configure(tenant, poolKey, request.expectedRevision(), request.memberUserIds());
    return ApiResponse.success(queries.pool(tenant, poolKey));
  }

  @GetMapping("/pools/{poolKey}/candidates")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'manage-routing')")
  @Operation(summary = "List current candidates, freshly filtered before pagination")
  public ApiResponse<PagedResponse<RoutingMemberResponse>> candidates(
      @PathVariable RoutingPoolKey poolKey,
      @RequestParam(required = false) String search,
      @ParameterObject Pageable pageable) {
    return ApiResponse.success(
        PagedResponse.from(
            queries.candidates(TenantContext.requireTenantId(), poolKey, search, pageable)));
  }

  @GetMapping("/tasks/{taskId}/eligibility")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'manage-routing')")
  @Operation(summary = "Evaluate members against the task's sales write scope")
  public ApiResponse<RoutingEligibilityResponse> eligibility(@PathVariable UUID taskId) {
    return ApiResponse.success(queries.eligibility(TenantContext.requireTenantId(), taskId));
  }

  @GetMapping("/failures")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'manage-routing')")
  @Operation(summary = "List routing failures and their delivery state without order details")
  public ApiResponse<PagedResponse<RoutingFailureResponse>> failures(
      @RequestParam(required = false) RoutingPoolKey poolKey,
      @RequestParam(required = false) Boolean open,
      @ParameterObject Pageable pageable) {
    return ApiResponse.success(
        PagedResponse.from(
            queries.failures(TenantContext.requireTenantId(), poolKey, open, pageable)));
  }

  @PostMapping("/pools/{poolKey}/repair")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'manage-routing')")
  @Operation(summary = "Re-evaluate all open governed tasks and retry owed alerts")
  public ApiResponse<RoutingRepairResponse> repair(@PathVariable RoutingPoolKey poolKey) {
    var result = repair.repair(TenantContext.requireTenantId(), poolKey, false);
    return ApiResponse.success(
        new RoutingRepairResponse(
            result.evaluated(),
            result.changed(),
            result.failuresOpened(),
            result.failuresResolved()),
        result.deliveryComplete()
            ? java.util.List.of()
            : java.util.List.of("ROUTING_ALERT_DELIVERY_INCOMPLETE"));
  }

  @ExceptionHandler(RoutingMembersRejectedException.class)
  public ResponseEntity<ApiResponse<Void>> rejected(RoutingMembersRejectedException error) {
    return ResponseEntity.unprocessableEntity()
        .body(
            ApiResponse.error(
                ApiResponse.ErrorDetail.builder()
                    .code("ROUTING_MEMBER_NOT_CANDIDATE")
                    .message(error.getMessage())
                    .field("memberUserIds")
                    .rejectedValue(error.getReasons())
                    .build()));
  }
}
