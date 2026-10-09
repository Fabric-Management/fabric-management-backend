package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditLeaseService;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseDtos;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Field leases of an open sales order (CEDIT-07 §4): which browser tab may change which key now.
 * Session ids and lease tokens travel in request bodies only, never in a URL. Changes to who holds
 * what are signalled on the order's live stream as a new {@code leaseRevision}; the client then
 * reads the list again. A lease grants no permission and replaces neither the safe save's merge nor
 * its validation.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Sales Order Edit Leases", description = "Who may change which field of an open order")
public class SalesOrderEditLeaseController {

  static final String PATH = "/api/v1/sales/orders/{orderId}/edit-leases";

  private final SalesOrderEditLeaseService leases;

  @PostMapping(PATH)
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "acquireSalesOrderEditLeases",
      summary = "I start changing these fields",
      description =
          "All keys are granted to my edit session or none. A key my session already holds keeps"
              + " its token (a repeat after a lost answer); any other grant has a new token."
              + " Requires current write access, an editable draft and my open edit session. 409"
              + " EDIT_LEASE_UNAVAILABLE names the holders in the way (no token); 409"
              + " EDIT_LEASE_LIMIT_REACHED;"
              + " 404 EDIT_SESSION_NOT_FOUND for an ended or foreign session; 422"
              + " EDIT_LEASE_KEY_INVALID for a wrong line or a header/line mix-up.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Granted leases with their tokens")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description =
          "EDIT_LEASE_UNAVAILABLE with holders; EDIT_LEASE_LIMIT_REACHED or an order state code"
              + " otherwise",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = SalesOrderEditLeaseProblem.class)))
  public ResponseEntity<ApiResponse<SalesOrderEditLeaseDtos.Grant>> acquireSalesOrderEditLeases(
      @PathVariable UUID orderId,
      @Valid @RequestBody SalesOrderEditLeaseDtos.AcquireRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(leases.acquire(orderId, request, actor(authentication))));
  }

  @PostMapping(PATH + "/renew")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "renewSalesOrderEditLeases",
      summary = "I am still changing these fields",
      description =
          "Extends my session's leases named by their tokens, each on its own. Call it only while"
              + " the person really works in the form (see the policy). A token that is no longer"
              + " mine (ended, taken over, the order left the draft) comes back in"
              + " lostLeaseTokens and is never revived. 404 EDIT_SESSION_NOT_FOUND when my edit"
              + " session ended.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "What is still held and what was lost")
  public ResponseEntity<ApiResponse<SalesOrderEditLeaseDtos.Renewal>> renewSalesOrderEditLeases(
      @PathVariable UUID orderId,
      @Valid @RequestBody SalesOrderEditLeaseDtos.TokensRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(leases.renew(orderId, request, actor(authentication))));
  }

  @PostMapping(PATH + "/release")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "releaseSalesOrderEditLeases",
      summary = "I stopped changing these fields",
      description =
          "Gives back my session's leases named by their tokens. Repeating it, an ended lease or a"
              + " token that is not mine changes nothing and still answers 204. Needs read access"
              + " only, so cleanup works after write access was lost.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "204",
      description = "Released, or nothing to release")
  public ResponseEntity<Void> releaseSalesOrderEditLeases(
      @PathVariable UUID orderId,
      @Valid @RequestBody SalesOrderEditLeaseDtos.TokensRequest request,
      Authentication authentication) {
    leases.release(orderId, request, actor(authentication));
    return ResponseEntity.noContent().build();
  }

  @GetMapping(PATH)
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "listSalesOrderEditLeases",
      summary = "Who is changing which field now",
      description =
          "Requires current read access to the active order. Held leases in key order with the"
              + " holder's name and expiry, never a token; my own leases carry their edit session"
              + " id, so this tab tells itself from my other tabs. Also the lease policy.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Held leases and the policy")
  public ResponseEntity<ApiResponse<SalesOrderEditLeaseDtos.Leases>> listSalesOrderEditLeases(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.ok(ApiResponse.success(leases.list(orderId, actor(authentication))));
  }

  private static UUID actor(Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedUserContext context
        && context.userId() != null) {
      return context.userId();
    }
    throw new AccessDeniedException("Authenticated user context is required.");
  }
}
