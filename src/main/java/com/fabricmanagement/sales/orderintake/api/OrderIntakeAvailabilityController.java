package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.orderintake.app.OrderIntakeAvailabilityService;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeAvailabilityDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/order-intake/availability")
@RequiredArgsConstructor
@Tag(name = "Order Intake", description = "Catalogue order entry, proposals and acceptance")
public class OrderIntakeAvailabilityController {

  private final OrderIntakeAvailabilityService availabilityService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getOrderIntakeAvailability",
      summary = "Stock a product has in one colour, as advisory whole-piece quantities",
      description =
          "Sales-readable projection of the piece-level stock the quantity evaluation uses. A"
              + " missing colour matches colourless lots only. Nothing is reserved.")
  public ResponseEntity<ApiResponse<OrderIntakeAvailabilityDto>> get(
      @Parameter(description = "Product identifier") @RequestParam UUID productId,
      @Parameter(description = "Colour card identifier; omit for colourless stock")
          @RequestParam(required = false)
          UUID colorId,
      @Parameter(description = "Customer, so confirmed lot compatibility is honoured")
          @RequestParam(required = false)
          UUID customerId) {
    return ResponseEntity.ok(
        ApiResponse.success(availabilityService.forProductColour(productId, colorId, customerId)));
  }
}
