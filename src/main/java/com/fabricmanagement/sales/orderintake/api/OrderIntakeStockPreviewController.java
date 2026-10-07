package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.orderintake.app.OrderIntakeStockPreviewService;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewDto;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/order-intake/stock-preview")
@RequiredArgsConstructor
@Tag(name = "Order Intake", description = "Catalogue order entry, proposals and acceptance")
public class OrderIntakeStockPreviewController {

  private final OrderIntakeStockPreviewService previewService;

  /** A query sent as a body; it has no side effects. */
  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "previewOrderIntakeStock",
      summary = "Current stock for an order line that is not saved yet",
      description =
          "Evaluates the line exactly as a saved line with the same inputs would be evaluated and"
              + " reports, beside the options, what other quotes hold on each lot and whether each"
              + " option is covered by free stock. Guidance for the salesperson only: nothing is"
              + " reserved or recorded, and the options are not changed by quote holds. A free"
              + " quantity that cannot be determined is null with its reasons.")
  public ResponseEntity<ApiResponse<OrderIntakeStockPreviewDto>> preview(
      @Valid @RequestBody OrderIntakeStockPreviewRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(previewService.preview(request, OrderIntakeActor.current())));
  }
}
