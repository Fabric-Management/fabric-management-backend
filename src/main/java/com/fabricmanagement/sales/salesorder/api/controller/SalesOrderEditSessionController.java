package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditSessionService;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditSessionDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditorsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who holds a sales order's edit form open (CEDIT-06 §3). A session is presence only: no save, lock
 * or capability depends on it. Changes to the list are signalled on the order's live stream as a
 * new {@code presenceRevision}; the client then reads this list again.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Sales Order Edit Sessions", description = "Who is editing an open sales order")
public class SalesOrderEditSessionController {

  static final String PATH = "/api/v1/sales/orders/{orderId}/edit-sessions";

  private final SalesOrderEditSessionService sessions;

  @PostMapping(PATH)
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "openSalesOrderEditSession",
      summary = "I opened this order's edit form",
      description =
          "Requires current write access to the active order. Every call opens a new session"
              + " (one per browser tab). Renew it after renewAfterSeconds and close it when the"
              + " form closes; an unrenewed session ends at expiresAt. 404 when the order is not"
              + " readable now, 403 when it is readable but not writable.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "201",
      description = "The new session")
  public ResponseEntity<ApiResponse<SalesOrderEditSessionDto>> openSalesOrderEditSession(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(sessions.open(orderId, actor(authentication))));
  }

  @PutMapping(PATH + "/{editSessionId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "renewSalesOrderEditSession",
      summary = "My edit form is still open",
      description =
          "Extends my own open session. 404 EDIT_SESSION_NOT_FOUND when it was closed, expired,"
              + " belongs to someone else or never existed: open a new session instead.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "The session with its new expiry")
  public ResponseEntity<ApiResponse<SalesOrderEditSessionDto>> renewSalesOrderEditSession(
      @PathVariable UUID orderId, @PathVariable UUID editSessionId, Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(sessions.renew(orderId, editSessionId, actor(authentication))));
  }

  @DeleteMapping(PATH + "/{editSessionId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "closeSalesOrderEditSession",
      summary = "I closed this order's edit form",
      description =
          "Closes my own session. Repeating it, or naming a session that is not mine, changes"
              + " nothing and still answers 204.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "204",
      description = "Closed, or nothing to close")
  public ResponseEntity<Void> closeSalesOrderEditSession(
      @PathVariable UUID orderId, @PathVariable UUID editSessionId, Authentication authentication) {
    sessions.close(orderId, editSessionId, actor(authentication));
    return ResponseEntity.noContent().build();
  }

  @GetMapping(PATH)
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "listSalesOrderEditors",
      summary = "Who has this order's edit form open now",
      description =
          "Requires current read access to the active order. Lists open sessions, oldest first,"
              + " including my own; viewers who only read are not listed.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Open edit sessions now")
  public ResponseEntity<ApiResponse<SalesOrderEditorsDto>> listSalesOrderEditors(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.ok(ApiResponse.success(sessions.editors(orderId, actor(authentication))));
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
