package com.fabricmanagement.finance.collection.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.finance.collection.app.CollectionChequeOfferService;
import com.fabricmanagement.finance.collection.app.CollectionCustomerSummaryService;
import com.fabricmanagement.finance.collection.dto.CollectionCustomerSummaryDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/collections")
@RequiredArgsConstructor
@Tag(name = "Finance Collections")
public class CollectionCustomerSummaryController {
  private final CollectionCustomerSummaryService summaries;
  private final CollectionChequeOfferService offers;

  @GetMapping("/customers/{partnerId}/summary")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(
      operationId = "getFinanceCollectionCustomerSummary",
      summary = "Read the customer's scoped collection follow-up and payment plan balances")
  public ResponseEntity<ApiResponse<Summary>> summary(
      @PathVariable UUID partnerId, Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(summaries.summary(partnerId, actor(auth))));
  }

  @PutMapping("/cheques/{chequeId}/offer")
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  @Operation(
      operationId = "recordFinanceCollectionChequeOffer",
      summary = "Confirm that a held cheque was offered for a transfer instalment")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description = "Cheque or transfer instalment is incompatible with the offer")
  public ResponseEntity<ApiResponse<Offer>> recordOffer(
      @PathVariable UUID chequeId, @Valid @RequestBody RecordOffer body, Authentication auth) {
    return ResponseEntity.ok(
        ApiResponse.success(offers.record(chequeId, body.instalmentId(), actor(auth))));
  }

  private static UUID actor(Authentication auth) {
    if (auth != null
        && auth.getPrincipal() instanceof AuthenticatedUserContext user
        && user.userId() != null) return user.userId();
    throw new AccessDeniedException("A signed-in finance user is required");
  }
}
