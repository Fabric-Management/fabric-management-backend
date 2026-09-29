package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.orderintake.app.CoverPlanningService;
import com.fabricmanagement.sales.orderintake.domain.CoverPortionKind;
import com.fabricmanagement.sales.orderintake.dto.FulfilmentDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Confirmations planning, the warehouse and logistics give on sales-order lines (SOI D5, D6,
 * IK-10): greige cover and production readiness ({@code production:write}), ship readiness of held
 * stock ({@code logistics:prepare}) and the sourced arrival estimate ({@code logistics:write}).
 */
@RestController
@RequestMapping("/api/v1/sales/order-intake")
@RequiredArgsConstructor
@Tag(name = "Fulfilment Planning", description = "Greige cover, readiness and arrival records")
public class FulfilmentPlanningController {

  private final CoverPlanningService coverPlanning;

  @PostMapping("/lines/{lineId}/greige-cover")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "confirmGreigeCover",
      summary = "Confirm the finished quantity of a line that is made from available greige")
  public ResponseEntity<ApiResponse<FulfilmentDtos.LineOutlook>> confirmGreigeCover(
      @PathVariable UUID lineId, @Valid @RequestBody FulfilmentDtos.ConfirmGreigeCover request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            coverPlanning.confirmGreigeCover(lineId, request, OrderIntakeActor.current())));
  }

  @PostMapping("/lines/{lineId}/greige-cover/withdraw")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(operationId = "withdrawGreigeCover", summary = "Withdraw a greige cover confirmation")
  public ResponseEntity<ApiResponse<FulfilmentDtos.LineOutlook>> withdrawGreigeCover(
      @PathVariable UUID lineId) {
    return ResponseEntity.ok(
        ApiResponse.success(coverPlanning.withdrawGreigeCover(lineId, OrderIntakeActor.current())));
  }

  @PostMapping("/lines/{lineId}/readiness/{portion}/confirm")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "confirmProductionReadiness",
      summary = "Planning confirms when a production portion will be ready")
  public ResponseEntity<ApiResponse<FulfilmentDtos.LineOutlook>> confirmProductionReadiness(
      @PathVariable UUID lineId,
      @PathVariable CoverPortionKind portion,
      @Valid @RequestBody FulfilmentDtos.ConfirmReadiness request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            coverPlanning.confirmProductionReadiness(
                lineId, portion, request, OrderIntakeActor.current())));
  }

  @PostMapping("/lines/{lineId}/ship-readiness/confirm")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'prepare')")
  @Operation(
      operationId = "confirmShipReadiness",
      summary = "The warehouse confirms when the held stock is ready to ship")
  public ResponseEntity<ApiResponse<FulfilmentDtos.LineOutlook>> confirmShipReadiness(
      @PathVariable UUID lineId, @Valid @RequestBody FulfilmentDtos.ConfirmReadiness request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            coverPlanning.confirmShipReadiness(lineId, request, OrderIntakeActor.current())));
  }

  @GetMapping("/readiness-requests/production")
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  @Operation(
      operationId = "listProductionReadinessRequests",
      summary = "Open readiness questions for production portions")
  public ResponseEntity<ApiResponse<List<FulfilmentDtos.ReadinessRequestView>>>
      listProductionReadinessRequests() {
    return ResponseEntity.ok(ApiResponse.success(coverPlanning.openRequests(false)));
  }

  @GetMapping("/readiness-requests/warehouse")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'prepare')")
  @Operation(
      operationId = "listWarehouseReadinessRequests",
      summary = "Open ship-readiness questions for held stock")
  public ResponseEntity<ApiResponse<List<FulfilmentDtos.ReadinessRequestView>>>
      listWarehouseReadinessRequests() {
    return ResponseEntity.ok(ApiResponse.success(coverPlanning.openRequests(true)));
  }

  @PutMapping("/orders/{orderId}/arrival-estimate")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'write')")
  @Operation(
      operationId = "recordArrivalEstimate",
      summary = "Record when the goods are expected at the customer, with its source")
  public ResponseEntity<ApiResponse<FulfilmentDtos.ArrivalView>> recordArrivalEstimate(
      @PathVariable UUID orderId,
      @Valid @RequestBody FulfilmentDtos.RecordArrivalEstimate request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            coverPlanning.recordArrival(orderId, request, OrderIntakeActor.current())));
  }
}
