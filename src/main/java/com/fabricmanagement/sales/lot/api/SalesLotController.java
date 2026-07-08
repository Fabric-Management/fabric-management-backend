package com.fabricmanagement.sales.lot.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.lot.app.SalesLotService;
import com.fabricmanagement.sales.lot.dto.SalesLotDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/lots")
@RequiredArgsConstructor
@Tag(name = "Sales Lot", description = "Sales-readable lot and piece projection")
public class SalesLotController {

  private final SalesLotService salesLotService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "listSalesLots",
      summary = "List sales-readable lots, pieces, and advisory ATP quantities")
  public ResponseEntity<ApiResponse<List<SalesLotDto>>> listSalesLots(
      @RequestParam(required = false) UUID quoteLineId) {
    return ResponseEntity.ok(ApiResponse.success(salesLotService.listSalesLots(quoteLineId)));
  }
}
