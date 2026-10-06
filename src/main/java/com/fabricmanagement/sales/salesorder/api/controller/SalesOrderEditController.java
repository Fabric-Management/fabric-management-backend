package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditConflictException;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditService;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditBase;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditConflictProblem;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOperationView;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The safe edit of a draft order (CEDIT-02): a server base when the form opens, and saves of only
 * the changed keys against it. These endpoints are separate from the expected-version commands and
 * from the legacy full-replace update; they do not change them.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Sales Order Safe Edit", description = "Edit bases and merged saves of draft orders")
public class SalesOrderEditController {

  private final SalesOrderEditService edits;

  @PostMapping("/api/v1/sales/orders/{orderId}/edit-bases")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "createSalesOrderEditBase",
      summary = "Open the edit form: a server base for this user",
      description =
          "Requires current write access and an editable draft. The base records the order as"
              + " read now; saves name it and are merged against it.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "201",
      description = "The base and the order as it was read for it")
  public ResponseEntity<ApiResponse<SalesOrderEditBase>> createSalesOrderEditBase(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(edits.openBase(orderId, actor(authentication), authentication)));
  }

  @PostMapping("/api/v1/sales/orders/{orderId}/edit-operations")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "saveSalesOrderEdit",
      summary = "Save changed keys against a base",
      description =
          "Only the keys sent change. A retry with the same operation id and content answers the"
              + " recorded result again. Conflicts save nothing and answer 409 EDIT_CONFLICT or"
              + " EDIT_BASE_EXPIRED with a new base to resolve against; other 409 codes carry"
              + " only the standard problem fields.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Saved (APPLIED) or nothing differed (NO_CHANGE)")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description =
          "EDIT_CONFLICT or EDIT_BASE_EXPIRED with conflicts and currentBase; EDIT_BASE_UNKNOWN,"
              + " OPERATION_ID_REUSED, LINE_ALREADY_ADDED or an order rule otherwise",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = SalesOrderEditConflictProblem.class)))
  public ResponseEntity<ApiResponse<SalesOrderEditResult>> saveSalesOrderEdit(
      @PathVariable UUID orderId,
      @Valid @RequestBody SalesOrderEditRequest request,
      Authentication authentication,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        ApiResponse.success(
            edits.save(
                orderId,
                request,
                actor(authentication),
                authentication,
                httpRequest.getRequestURI())));
  }

  @GetMapping("/api/v1/sales/orders/{orderId}/edit-operations/{operationId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getSalesOrderEditOperation",
      summary = "The recorded outcome of one of my saves",
      description =
          "For diagnosis only. Not found is not proof that a save failed: repeat the same save"
              + " to learn its result.")
  public ResponseEntity<ApiResponse<SalesOrderEditOperationView>> getSalesOrderEditOperation(
      @PathVariable UUID orderId, @PathVariable UUID operationId, Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(edits.operation(orderId, operationId, actor(authentication))));
  }

  /** A recorded conflict, answered exactly as recorded; nothing was rolled back. */
  @ExceptionHandler(SalesOrderEditConflictException.class)
  public ResponseEntity<JsonNode> handleEditConflict(SalesOrderEditConflictException conflict) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(conflict.body());
  }

  private static UUID actor(Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedUserContext context) {
      return context.userId();
    }
    throw new AccessDeniedException("Authenticated user context is required.");
  }
}
