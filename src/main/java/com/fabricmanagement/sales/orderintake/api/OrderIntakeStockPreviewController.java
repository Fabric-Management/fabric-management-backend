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
          "Evaluates the line exactly as a saved line with the same inputs. The explicit policy"
              + " determines business order: fabric rolls complete the request with healthy stock"
              + " and cleanup priority, even outside tolerance, then show the below-request alternative."
              + " Held stock is informational and appears only when free stock cannot reach the lower"
              + " tolerance. mainOptionIndex identifies the opening; coverage separately states proven"
              + " feasibility. Manual picks use the same eligibility, hold and single-lot checks."
              + " This query reserves and records nothing; unknown free quantities remain null.")
  public ResponseEntity<ApiResponse<OrderIntakeStockPreviewDto>> preview(
      @Valid @RequestBody OrderIntakeStockPreviewRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(previewService.preview(request, OrderIntakeActor.current())));
  }
}
