package com.fabricmanagement.finance.collection.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.finance.collection.app.CollectionChequeDrawerService;
import com.fabricmanagement.finance.collection.dto.CollectionChequeDrawerDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cheque drawers for the cheque entry form (PAYMENT-PLAN-7 R15). Every endpoint uses the permission
 * that receives a cheque, so whoever records a cheque can complete its drawer in one step.
 */
@RestController
@RequestMapping("/api/v1/finance/collections/customers/{partnerId}/cheque-drawers")
@RequiredArgsConstructor
@Tag(
    name = "Finance Collection Cheque Drawers",
    description = "Cheque drawers for receiving cheques")
public class CollectionChequeDrawerController {

  private final CollectionChequeDrawerService drawers;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  @Operation(
      operationId = "listFinanceCollectionChequeDrawers",
      summary = "The customer and its active cheque drawers, with the kind a new cheque starts on")
  public ResponseEntity<ApiResponse<CollectionChequeDrawerDtos.Drawers>> list(
      @PathVariable UUID partnerId) {
    return ResponseEntity.ok(ApiResponse.success(drawers.drawers(partnerId)));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  @Operation(
      operationId = "addFinanceCollectionChequeDrawer",
      summary = "Add a cheque drawer from the cheque form; an existing name is returned instead",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "A new drawer was recorded (outcome CREATED)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "The name already exists: outcome EXISTING or INACTIVE_DUPLICATE"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Customer not found in this tenant"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "CHEQUE_DRAWER_PARTNER_NOT_CUSTOMER or CHEQUE_DRAWER_PARTNER_REMOVED")
      })
  public ResponseEntity<ApiResponse<CollectionChequeDrawerDtos.AddResult>> add(
      @PathVariable UUID partnerId,
      @Valid @RequestBody CollectionChequeDrawerDtos.AddRequest request) {
    var result = drawers.add(partnerId, request);
    return ResponseEntity.status(
            result.outcome() == CollectionChequeDrawerDtos.AddOutcome.CREATED
                ? HttpStatus.CREATED
                : HttpStatus.OK)
        .body(ApiResponse.success(result));
  }

  @PostMapping("/{drawerId}/reactivation")
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  @Operation(
      operationId = "reactivateFinanceCollectionChequeDrawer",
      summary = "Reactivate a deactivated cheque drawer so it can be selected")
  public ResponseEntity<ApiResponse<CollectionChequeDrawerDtos.Drawer>> reactivate(
      @PathVariable UUID partnerId, @PathVariable UUID drawerId) {
    return ResponseEntity.ok(ApiResponse.success(drawers.reactivate(partnerId, drawerId)));
  }
}
