package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.orderintake.app.CoverPlanningService;
import com.fabricmanagement.sales.orderintake.app.LineHoldService;
import com.fabricmanagement.sales.orderintake.app.ProductCorrectionService;
import com.fabricmanagement.sales.orderintake.dto.FulfilmentDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
 * The salesperson's view and requests after intake: cover portions and readiness, product
 * correction and hold requests (SOI D5, D6, D8).
 */
@RestController
@RequestMapping("/api/v1/sales-orders/{orderId}/intake")
@RequiredArgsConstructor
@Tag(name = "Order Intake", description = "Catalogue order entry, proposals and acceptance")
public class OrderFulfilmentController {

  private final CoverPlanningService coverPlanning;
  private final ProductCorrectionService productCorrection;
  private final LineHoldService lineHolds;

  @GetMapping("/delivery-outlook")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getOrderDeliveryOutlook",
      summary = "Cover portions, confirmed readiness per portion and the sourced arrival estimate")
  public ResponseEntity<ApiResponse<FulfilmentDtos.DeliveryOutlook>> getOrderDeliveryOutlook(
      @PathVariable UUID orderId) {
    return ResponseEntity.ok(
        ApiResponse.success(coverPlanning.outlook(orderId, OrderIntakeActor.current())));
  }

  @PostMapping("/lines/{lineId}/readiness-requests")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "requestPortionReadiness",
      summary = "Ask planning or the warehouse to confirm when a portion will be ready")
  public ResponseEntity<ApiResponse<FulfilmentDtos.LineOutlook>> requestPortionReadiness(
      @PathVariable UUID orderId,
      @PathVariable UUID lineId,
      @Valid @RequestBody FulfilmentDtos.RequestReadiness request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            coverPlanning.requestReadiness(
                orderId, lineId, request.portion(), OrderIntakeActor.current())));
  }

  @PostMapping("/product-corrections")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "correctLineProduct",
      summary = "Replace a wrongly chosen product on all of its distributions in the order")
  public ResponseEntity<ApiResponse<List<FulfilmentDtos.CorrectionView>>> correctLineProduct(
      @PathVariable UUID orderId, @Valid @RequestBody FulfilmentDtos.CorrectProduct request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            productCorrection.correct(orderId, request, OrderIntakeActor.current())));
  }

  @GetMapping("/product-corrections")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "listLineProductCorrections",
      summary = "Product corrections of the order")
  public ResponseEntity<ApiResponse<List<FulfilmentDtos.CorrectionView>>>
      listLineProductCorrections(@PathVariable UUID orderId) {
    return ResponseEntity.ok(
        ApiResponse.success(productCorrection.history(orderId, OrderIntakeActor.current())));
  }

  @PostMapping("/lines/{lineId}/hold-requests")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "requestLineHold",
      summary = "Ask production to hold the line's running work; not a stop until confirmed")
  public ResponseEntity<ApiResponse<List<FulfilmentDtos.HoldView>>> requestLineHold(
      @PathVariable UUID orderId,
      @PathVariable UUID lineId,
      @Valid @RequestBody FulfilmentDtos.RequestHold request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                lineHolds.request(orderId, lineId, request, OrderIntakeActor.current())));
  }

  @GetMapping("/lines/{lineId}/hold-requests")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(operationId = "listLineHolds", summary = "Hold requests and their state for a line")
  public ResponseEntity<ApiResponse<List<FulfilmentDtos.HoldView>>> listLineHolds(
      @PathVariable UUID orderId, @PathVariable UUID lineId) {
    return ResponseEntity.ok(
        ApiResponse.success(lineHolds.forLine(orderId, lineId, OrderIntakeActor.current())));
  }
}
