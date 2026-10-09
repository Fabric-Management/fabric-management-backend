package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.salesorder.app.SalesOrderFieldHistoryService;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The saved field changes of a sales order (CEDIT-09 §3): read-only, for anyone who may read the
 * order now. Only changes saved through the safe edit are recorded here; other commands do not
 * write this history, so an empty list does not mean the order never changed.
 */
@RestController
@RequiredArgsConstructor
@Tag(
    name = "Sales Order Field History",
    description = "Who changed which field of an order, when, from what to what")
public class SalesOrderFieldHistoryController {

  static final String PATH = "/api/v1/sales/orders/{orderId}/field-history";

  private final SalesOrderFieldHistoryService history;

  @GetMapping(PATH)
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getSalesOrderFieldHistory",
      summary = "Saved field changes of the order, newest first",
      description =
          "Field changes saved with the safe edit: each changed key with the value before and"
              + " after, whole lines added or removed, who saved it, when, in which save and the"
              + " resulting order version, and the conflict decision if one decided it. Changes"
              + " of every person are shown. Other commands (legacy update, product correction,"
              + " quantity acceptance, lifecycle steps) do not write this history; an empty list"
              + " does not mean the order never changed. Requires current read access to the"
              + " active order, in any status; no write access, edit session or lease. Read-only:"
              + " nothing is opened, locked or written. Pages are stable: the first page fixes"
              + " snapshotVersion and later pages (cursor) never show a newer change; read"
              + " without a cursor to start again from the newest. 422 VALIDATION_ERROR names"
              + " limit or cursor in errors; 404 for an order that does not exist or cannot be"
              + " read now.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "One page of the history")
  public ResponseEntity<ApiResponse<SalesOrderFieldHistoryDtos.Page>> getSalesOrderFieldHistory(
      @PathVariable UUID orderId,
      @Parameter(
              description = "Entries per page, 1 to 100",
              schema =
                  @Schema(
                      type = "integer",
                      format = "int32",
                      minimum = "1",
                      maximum = "100",
                      defaultValue = "30"))
          @RequestParam(name = "limit", required = false)
          Integer limit,
      @Parameter(
              description =
                  "nextCursor of the previous page, unchanged; omit it for the newest page",
              schema =
                  @Schema(
                      type = "string",
                      maxLength = SalesOrderFieldHistoryDtos.MAX_CURSOR_LENGTH))
          @RequestParam(name = "cursor", required = false)
          String cursor,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(history.page(orderId, limit, cursor, actor(authentication))));
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
