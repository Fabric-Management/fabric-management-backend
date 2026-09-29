package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.orderintake.app.AgreedToleranceService;
import com.fabricmanagement.sales.orderintake.app.CustomerToneAcceptanceService;
import com.fabricmanagement.sales.orderintake.app.OrderIntakeReadinessService;
import com.fabricmanagement.sales.orderintake.app.OrderIntakeViews;
import com.fabricmanagement.sales.orderintake.app.QuantityAcceptanceService;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluationService;
import com.fabricmanagement.sales.orderintake.dto.CustomerToneAcceptanceDto;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeReadinessDto;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeRequests;
import com.fabricmanagement.sales.orderintake.dto.QuantityAcceptanceDto;
import com.fabricmanagement.sales.orderintake.dto.QuantityProposalDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Whole-piece quantity proposals, stock choices and customer acceptance, tone acceptances, agreed
 * tolerance and confirmation readiness (SOI D2–D4).
 */
@RestController
@RequestMapping("/api/v1/sales-orders/{orderId}/intake")
@RequiredArgsConstructor
@Tag(name = "Order Intake", description = "Catalogue order entry, proposals and acceptance")
public class OrderIntakeController {

  private final QuantityEvaluationService quantityEvaluation;
  private final CustomerToneAcceptanceService toneAcceptance;
  private final AgreedToleranceService agreedTolerance;
  private final QuantityAcceptanceService quantityAcceptance;
  private final OrderIntakeReadinessService readiness;

  @PostMapping("/lines/{lineId}/quantity-proposals")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "evaluateLineQuantity",
      summary = "Evaluate the requested quantity against whole pieces in stock; reserves nothing")
  public ResponseEntity<ApiResponse<QuantityProposalDto>> evaluateLineQuantity(
      @PathVariable UUID orderId, @PathVariable UUID lineId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                quantityEvaluation.evaluate(orderId, lineId, OrderIntakeActor.current())));
  }

  @GetMapping("/lines/{lineId}/quantity-proposals/latest")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getLatestQuantityProposal",
      summary = "The latest recorded quantity proposal of a line, or null")
  public ResponseEntity<ApiResponse<QuantityProposalDto>> getLatestQuantityProposal(
      @PathVariable UUID orderId, @PathVariable UUID lineId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            quantityEvaluation.latest(orderId, lineId, OrderIntakeActor.current()).orElse(null)));
  }

  @PostMapping("/tone-acceptances")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "recordCustomerToneAcceptance",
      summary = "Record that the customer accepted a concrete shade difference between lots")
  public ResponseEntity<ApiResponse<CustomerToneAcceptanceDto>> recordToneAcceptance(
      @PathVariable UUID orderId,
      @Valid @RequestBody OrderIntakeRequests.RecordToneAcceptance request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                toneAcceptance.record(orderId, request, OrderIntakeActor.current())));
  }

  @GetMapping("/tone-acceptances")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "listCustomerToneAcceptances",
      summary = "Tone acceptances recorded for the order's customer")
  public ResponseEntity<ApiResponse<List<CustomerToneAcceptanceDto>>> listToneAcceptances(
      @PathVariable UUID orderId) {
    return ResponseEntity.ok(
        ApiResponse.success(toneAcceptance.forOrder(orderId, OrderIntakeActor.current())));
  }

  @GetMapping("/agreed-tolerance")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getAgreedQuantityTolerance",
      summary = "The quantity tolerance agreed with the customer; null fields when none was agreed")
  public ResponseEntity<ApiResponse<OrderIntakeViews.AgreedTolerance>> getAgreedTolerance(
      @PathVariable UUID orderId) {
    return ResponseEntity.ok(
        ApiResponse.success(agreedTolerance.current(orderId, OrderIntakeActor.current())));
  }

  @PutMapping("/agreed-tolerance")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "recordAgreedQuantityTolerance",
      summary = "Record or clear the quantity tolerance agreed with the customer")
  public ResponseEntity<ApiResponse<OrderIntakeViews.AgreedTolerance>> recordAgreedTolerance(
      @PathVariable UUID orderId,
      @Valid @RequestBody OrderIntakeRequests.RecordAgreedTolerance request) {
    return ResponseEntity.ok(
        ApiResponse.success(agreedTolerance.record(orderId, request, OrderIntakeActor.current())));
  }

  @PostMapping("/lines/{lineId}/quantity-acceptances")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "recordQuantityAcceptance",
      summary =
          "Record the stock option a line takes and the customer's acceptance; reserves nothing")
  public ResponseEntity<ApiResponse<QuantityAcceptanceDto>> recordQuantityAcceptance(
      @PathVariable UUID orderId,
      @PathVariable UUID lineId,
      @Valid @RequestBody OrderIntakeRequests.RecordQuantityAcceptance request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                quantityAcceptance.record(orderId, lineId, request, OrderIntakeActor.current())));
  }

  @GetMapping("/lines/{lineId}/quantity-acceptances")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "listQuantityAcceptances",
      summary = "Stock choices and acceptances recorded for a line, newest first")
  public ResponseEntity<ApiResponse<List<QuantityAcceptanceDto>>> listQuantityAcceptances(
      @PathVariable UUID orderId, @PathVariable UUID lineId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            quantityAcceptance.history(orderId, lineId, OrderIntakeActor.current())));
  }

  @DeleteMapping("/lines/{lineId}/quantity-acceptances/active")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "withdrawQuantityAcceptance",
      summary = "Withdraw the line's active stock choice")
  public ResponseEntity<Void> withdrawQuantityAcceptance(
      @PathVariable UUID orderId, @PathVariable UUID lineId) {
    quantityAcceptance.withdraw(orderId, lineId, OrderIntakeActor.current());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/readiness")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getOrderIntakeReadiness",
      summary = "What blocks confirmation, what is held, and which intake actions are allowed")
  public ResponseEntity<ApiResponse<OrderIntakeReadinessDto>> getOrderIntakeReadiness(
      @PathVariable UUID orderId) {
    return ResponseEntity.ok(
        ApiResponse.success(readiness.readiness(orderId, OrderIntakeActor.current())));
  }
}
