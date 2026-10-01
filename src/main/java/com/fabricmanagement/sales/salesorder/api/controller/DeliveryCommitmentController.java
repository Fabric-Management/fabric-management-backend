package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.salesorder.app.DeliveryCommitmentService;
import com.fabricmanagement.sales.salesorder.dto.DeliveryCommitmentDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivery terms and the delivery commitments agreed with the buyer. There is no manual entry of a
 * commitment: it is recorded when the customer approves the sent order version.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Delivery Commitments", description = "Incoterms and agreed delivery promises")
public class DeliveryCommitmentController {

  private final DeliveryCommitmentService service;

  @GetMapping("/api/v1/sales/delivery-terms")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "listDeliveryTerms",
      summary = "Incoterms rules with the event their dates refer to")
  public ResponseEntity<ApiResponse<List<DeliveryCommitmentDtos.DeliveryTermOption>>>
      listDeliveryTerms() {
    return ResponseEntity.ok(ApiResponse.success(service.deliveryTerms()));
  }

  @GetMapping("/api/v1/sales/orders/{orderId}/delivery-commitments")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getDeliveryCommitments",
      summary = "The order's delivery commitments, first promise first, with their shifts")
  public ResponseEntity<ApiResponse<DeliveryCommitmentDtos.CommitmentHistory>>
      getDeliveryCommitments(@PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(service.history(orderId, currentUserId(authentication))));
  }

  private static UUID currentUserId(Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedUserContext context) {
      return context.userId();
    }
    throw new AccessDeniedException("Authenticated user context is required.");
  }
}
