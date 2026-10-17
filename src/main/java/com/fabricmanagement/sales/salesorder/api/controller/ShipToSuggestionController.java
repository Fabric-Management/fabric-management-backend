package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.salesorder.app.ShipToSuggestionService;
import com.fabricmanagement.sales.salesorder.dto.ShipToSuggestionDtos.Suggestion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Which of a consignee's address cards the order page offers first (CONSIGNEE-1 C11). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Sales Order Deliveries", description = "Deliveries and line allocation")
public class ShipToSuggestionController {

  private final ShipToSuggestionService suggestions;

  @GetMapping("/api/v1/sales/partners/{partnerId}/ship-to-suggestion")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getShipToSuggestion",
      summary =
          "The consignee's address card to preselect: most used verified, else shipping,"
              + " else primary")
  public ResponseEntity<ApiResponse<Suggestion>> getShipToSuggestion(@PathVariable UUID partnerId) {
    return ResponseEntity.ok(ApiResponse.success(suggestions.suggest(partnerId)));
  }
}
