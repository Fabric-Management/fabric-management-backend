package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.salesorder.app.ApprovalAuthorityService;
import com.fabricmanagement.sales.salesorder.app.OrderPartiesService;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.ApprovalAuthorityView;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.GrantApprovalAuthorityRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.OrderPartiesView;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.RevokeApprovalAuthorityRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.SetApproverRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.SetBillToRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.SetReleasePolicyRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.SetRequestedDateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The order's parties and order-level defaults, and who may approve for a customer (ADR-0014 D4,
 * D7, D9). Each write carries the order version it was based on; a stale write is a conflict.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Sales Order Parties", description = "Bill-to, approver, requested date, release")
public class OrderPartiesController {

  private final OrderPartiesService parties;
  private final ApprovalAuthorityService authorities;

  @GetMapping("/api/v1/sales/orders/{orderId}/parties")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(operationId = "getOrderParties", summary = "The order's parties and defaults")
  public ResponseEntity<ApiResponse<OrderPartiesView>> getOrderParties(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.ok(ApiResponse.success(parties.read(orderId, actor(authentication))));
  }

  @PutMapping("/api/v1/sales/orders/{orderId}/bill-to")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "setOrderBillTo",
      summary = "Who is invoiced: the customer, another partner or an unregistered party",
      description =
          "Invoicing someone other than the customer records the relationship and the reason;"
              + " finance accepts it before commercial approval.")
  public ResponseEntity<ApiResponse<OrderPartiesView>> setOrderBillTo(
      @PathVariable UUID orderId,
      @Valid @RequestBody SetBillToRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(parties.setBillTo(orderId, request, actor(authentication))));
  }

  @PutMapping("/api/v1/sales/orders/{orderId}/requested-date")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "setOrderRequestedDate",
      summary = "The order-level requested date with the event the customer meant")
  public ResponseEntity<ApiResponse<OrderPartiesView>> setOrderRequestedDate(
      @PathVariable UUID orderId,
      @Valid @RequestBody SetRequestedDateRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(parties.setRequestedDate(orderId, request, actor(authentication))));
  }

  @PutMapping("/api/v1/sales/orders/{orderId}/release-policy")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "setOrderReleasePolicy",
      summary = "Whether all production quantities must be released together")
  public ResponseEntity<ApiResponse<OrderPartiesView>> setOrderReleasePolicy(
      @PathVariable UUID orderId,
      @Valid @RequestBody SetReleasePolicyRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(parties.setReleasePolicy(orderId, request, actor(authentication))));
  }

  @PutMapping("/api/v1/sales/orders/{orderId}/approver")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "setOrderApprover",
      summary = "The customer contact who approves this order",
      description = "Only a contact holding an approval authority valid today qualifies.")
  public ResponseEntity<ApiResponse<OrderPartiesView>> setOrderApprover(
      @PathVariable UUID orderId,
      @Valid @RequestBody SetApproverRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(parties.setApprover(orderId, request, actor(authentication))));
  }

  @GetMapping("/api/v1/sales/customers/{customerId}/approval-authorities")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "listApprovalAuthorities",
      summary = "Who may approve orders for the customer, revoked authorities included")
  public ResponseEntity<ApiResponse<List<ApprovalAuthorityView>>> listApprovalAuthorities(
      @PathVariable UUID customerId) {
    return ResponseEntity.ok(ApiResponse.success(authorities.list(customerId)));
  }

  @PostMapping("/api/v1/sales/customers/{customerId}/approval-authorities")
  @PreAuthorize("@auth.can(authentication, 'sales', 'grant-approval-authority')")
  @Operation(
      operationId = "grantApprovalAuthority",
      summary = "Grant a customer contact the authority to approve orders, with its basis")
  public ResponseEntity<ApiResponse<ApprovalAuthorityView>> grantApprovalAuthority(
      @PathVariable UUID customerId,
      @Valid @RequestBody GrantApprovalAuthorityRequest request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(authorities.grant(customerId, request, actor(authentication))));
  }

  @PostMapping("/api/v1/sales/customers/{customerId}/approval-authorities/{authorityId}/revoke")
  @PreAuthorize("@auth.can(authentication, 'sales', 'grant-approval-authority')")
  @Operation(operationId = "revokeApprovalAuthority", summary = "Revoke an approval authority")
  public ResponseEntity<ApiResponse<ApprovalAuthorityView>> revokeApprovalAuthority(
      @PathVariable UUID customerId,
      @PathVariable UUID authorityId,
      @Valid @RequestBody RevokeApprovalAuthorityRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(
            authorities.revoke(customerId, authorityId, request, actor(authentication))));
  }

  private static UUID actor(Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedUserContext context) {
      return context.userId();
    }
    throw new AccessDeniedException("Authenticated user context is required.");
  }
}
