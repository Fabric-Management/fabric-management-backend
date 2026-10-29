package com.fabricmanagement.finance.collection.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.finance.collection.app.CollectionCommandService;
import com.fabricmanagement.finance.collection.app.CollectionQueryService;
import com.fabricmanagement.finance.collection.dto.CollectionDtos;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/collections")
@RequiredArgsConstructor
@Tag(
    name = "Finance Collections",
    description = "Agreed collection expectations, actual payment matching and cheque evidence")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "409",
    description =
        "Resource version, balance, cheque state, financial period or operation replay conflict",
    content =
        @io.swagger.v3.oas.annotations.media.Content(
            mediaType = "application/problem+json",
            schema =
                @io.swagger.v3.oas.annotations.media.Schema(
                    implementation = CollectionDtos.Conflict.class)))
public class CollectionController {
  private final CollectionQueryService queries;
  private final CollectionCommandService commands;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(
      operationId = "listFinanceCollections",
      summary = "Read paged expectations with per-currency balances and source freshness")
  public ResponseEntity<ApiResponse<Page<CollectionDtos.Obligation>>> list(
      Authentication auth,
      @RequestParam(required = false) UUID partnerId,
      @RequestParam(required = false) UUID orderId,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) Boolean cancelled,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Pageable page) {
    return ResponseEntity.ok(
        ApiResponse.success(
            queries.list(actor(auth), partnerId, orderId, currency, cancelled, from, to, page)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(
      operationId = "getFinanceCollection",
      summary = "Read a current expectation and paged payment-match evidence")
  public ResponseEntity<ApiResponse<CollectionDtos.Detail>> detail(
      @PathVariable UUID id, Authentication auth, Pageable page) {
    return ResponseEntity.ok(ApiResponse.success(queries.detail(id, actor(auth), page)));
  }

  @GetMapping("/{id}/expectations")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(
      operationId = "listFinanceCollectionExpectations",
      summary = "Read immutable accepted expectation history")
  public ResponseEntity<ApiResponse<Page<CollectionDtos.Expectation>>> expectations(
      @PathVariable UUID id, Authentication auth, Pageable page) {
    return ResponseEntity.ok(ApiResponse.success(queries.history(id, actor(auth), page)));
  }

  @GetMapping("/{id}/cheques")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(
      operationId = "listFinanceCollectionChequeIds",
      summary = "Read paged instrument identities applied to an expectation")
  public ResponseEntity<ApiResponse<Page<UUID>>> chequeIds(
      @PathVariable UUID id, Authentication auth, Pageable page) {
    return ResponseEntity.ok(ApiResponse.success(queries.chequeIds(id, actor(auth), page)));
  }

  @PostMapping("/{id}/payment-matches")
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  @Operation(
      operationId = "matchFinanceCollectionPayment",
      summary = "Explicitly match an existing inbound Payment with durable replay protection")
  public ResponseEntity<ApiResponse<CollectionDtos.Result>> match(
      @PathVariable UUID id,
      @Valid @RequestBody CollectionRequests.Match body,
      Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(commands.match(id, body, actor(auth))));
  }

  @PostMapping("/{id}/receipts")
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  @Operation(
      operationId = "recordFinanceCollectionReceipt",
      summary = "Atomically create one Payment and match it to the expectation")
  public ResponseEntity<ApiResponse<CollectionDtos.Result>> receive(
      @PathVariable UUID id,
      @Valid @RequestBody CollectionRequests.Receipt body,
      Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(commands.receive(id, body, actor(auth))));
  }

  @PostMapping("/payment-matches/{id}/reverse")
  @PreAuthorize("@auth.can(authentication, 'finance', 'reverse')")
  @Operation(
      operationId = "reverseFinanceCollectionMatch",
      summary = "Reverse a match with effective date and reason; retain its Payment")
  public ResponseEntity<ApiResponse<CollectionDtos.Result>> reverse(
      @PathVariable UUID id,
      @Valid @RequestBody CollectionRequests.Reverse body,
      Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(commands.reverse(id, body, actor(auth))));
  }

  @GetMapping("/{id}/refunds")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(
      operationId = "listFinanceCollectionRefunds",
      summary = "Read the refunds an expectation's surplus opened, with their current state")
  public ResponseEntity<ApiResponse<List<CollectionDtos.Refund>>> refunds(
      @PathVariable UUID id, Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(queries.refunds(id, actor(auth))));
  }

  @PostMapping("/{id}/over-settlement-resolutions")
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  @Operation(
      operationId = "resolveFinanceCollectionOverSettlement",
      summary =
          "Decide what cash above the agreed amount is: moved to another instalment, kept as an"
              + " advance, or owed back as a refund")
  public ResponseEntity<ApiResponse<CollectionDtos.Result>> resolveOverSettlement(
      @PathVariable UUID id,
      @Valid @RequestBody CollectionRequests.ResolveOverSettlement body,
      Authentication auth) {
    return ResponseEntity.ok(
        ApiResponse.success(commands.resolveOverSettlement(id, body, actor(auth))));
  }

  @PostMapping("/refunds/{id}/settlement")
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  @Operation(
      operationId = "settleFinanceCollectionRefund",
      summary = "Record that an outbound Payment of exactly the refund amount paid it back")
  public ResponseEntity<ApiResponse<CollectionDtos.Result>> settleRefund(
      @PathVariable UUID id,
      @Valid @RequestBody CollectionRequests.SettleRefund body,
      Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(commands.settleRefund(id, body, actor(auth))));
  }

  @PostMapping("/refunds/{id}/cancellation")
  @PreAuthorize("@auth.can(authentication, 'finance', 'reverse')")
  @Operation(
      operationId = "cancelFinanceCollectionRefund",
      summary = "Stop owing an unpaid refund; the money stays on account")
  public ResponseEntity<ApiResponse<CollectionDtos.Result>> cancelRefund(
      @PathVariable UUID id,
      @Valid @RequestBody CollectionRequests.CancelRefund body,
      Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(commands.cancelRefund(id, body, actor(auth))));
  }

  @PostMapping("/cheques")
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  @Operation(
      operationId = "receiveFinanceCollectionCheque",
      summary = "Record a physical cheque and its applications without recording cash")
  public ResponseEntity<ApiResponse<CollectionDtos.Result>> receiveCheque(
      @Valid @RequestBody CollectionRequests.ReceiveCheque body, Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(commands.receiveCheque(body, actor(auth))));
  }

  @GetMapping("/cheques/{id}")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(
      operationId = "getFinanceCollectionCheque",
      summary = "Read cheque evidence, clearance validity and available transitions")
  public ResponseEntity<ApiResponse<CollectionDtos.Cheque>> cheque(
      @PathVariable UUID id, Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(queries.cheque(id, actor(auth))));
  }

  @GetMapping("/cheques/{id}/events")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  @Operation(
      operationId = "listFinanceCollectionChequeEvents",
      summary = "Read paged immutable cheque receipt, correction and transition evidence")
  public ResponseEntity<ApiResponse<Page<CollectionDtos.ChequeEvent>>> chequeEvents(
      @PathVariable UUID id, Authentication auth, Pageable page) {
    return ResponseEntity.ok(ApiResponse.success(queries.chequeHistory(id, actor(auth), page)));
  }

  @PostMapping("/cheques/{id}/transitions")
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  @Operation(
      operationId = "transitionFinanceCollectionCheque",
      summary =
          "Present, clear, dishonour, return, cancel or correct an instrument with explicit"
              + " evidence")
  public ResponseEntity<ApiResponse<CollectionDtos.Result>> transition(
      @PathVariable UUID id,
      @Valid @RequestBody CollectionRequests.Transition body,
      Authentication auth) {
    return ResponseEntity.ok(ApiResponse.success(commands.transition(id, body, actor(auth))));
  }

  private static UUID actor(Authentication auth) {
    if (auth != null
        && auth.getPrincipal() instanceof AuthenticatedUserContext user
        && user.userId() != null) return user.userId();
    throw new AccessDeniedException("A signed-in finance user is required");
  }
}
