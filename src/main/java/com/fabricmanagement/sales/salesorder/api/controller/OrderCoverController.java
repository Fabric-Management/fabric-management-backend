package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.salesorder.app.*;
import com.fabricmanagement.sales.salesorder.dto.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sales-orders/{orderId}/cover")
@Tag(
    name = "Sales Orders \u2014 Cover",
    description = "Order-cover case, evidence and decision receipts")
public class OrderCoverController {
  private final OrderCoverQueryService query;
  private final OrderCoverEvidenceService evidence;
  private final com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator
      permissions;
  private final ConcurrentMap<String, Long> refreshes = new ConcurrentHashMap<>();

  public OrderCoverController(
      OrderCoverQueryService query,
      OrderCoverEvidenceService evidence,
      com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator permissions) {
    this.query = query;
    this.evidence = evidence;
    this.permissions = permissions;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<OrderCoverDetail>> getOrderCoverCase(
      @PathVariable UUID orderId) {
    assertReadGrants();
    return ResponseEntity.ok(ApiResponse.success(query.detail(orderId, actor())));
  }

  @PostMapping("/evidence")
  public ResponseEntity<ApiResponse<OrderCoverEvidenceDto>> refreshOrderCoverEvidence(
      @PathVariable UUID orderId) {
    assertReadGrants();
    UUID actor = actor();
    OrderCoverDetail detail = query.detail(orderId, actor);
    String key = TenantContext.requireTenantId() + ":" + actor + ":" + detail.caseData().id();
    long now = System.nanoTime();
    Long prior = refreshes.put(key, now);
    if (prior != null && now - prior < 2_000_000_000L)
      throw new com.fabricmanagement.common.infrastructure.web.exception.TooManyRequestsException(
          "Evidence refresh is rate limited");
    return ResponseEntity.ok(
        ApiResponse.success(evidence.refresh(orderId, detail.caseData().id())));
  }

  @GetMapping("/results/{resultId}")
  public ResponseEntity<ApiResponse<OrderCoverResultDto>> getOrderCoverResult(
      @PathVariable UUID orderId, @PathVariable UUID resultId) {
    assertReadGrants();
    return ResponseEntity.ok(ApiResponse.success(query.result(orderId, resultId, actor())));
  }

  private UUID actor() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getDetails() instanceof AuthenticatedUserContext context)
      return context.userId();
    throw new NotFoundException("Authenticated user context not found");
  }

  private void assertReadGrants() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!permissions.can(authentication, "flowboard", "read")
        || !permissions.can(authentication, "sales", "read"))
      throw new NotFoundException("Sales order not found");
  }
}
