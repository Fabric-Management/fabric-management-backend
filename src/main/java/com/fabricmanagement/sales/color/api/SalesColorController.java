package com.fabricmanagement.sales.color.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.color.app.SalesColorService;
import com.fabricmanagement.sales.color.dto.SalesColorDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/colors")
@RequiredArgsConstructor
@Tag(name = "Sales Color", description = "Sales-readable color-card projection")
public class SalesColorController {

  private final SalesColorService salesColorService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(operationId = "listSalesColors", summary = "List sales-readable color cards")
  public ResponseEntity<ApiResponse<List<SalesColorDto>>> listSalesColors(
      @RequestParam(defaultValue = "false") boolean includeInactive) {
    return ResponseEntity.ok(
        ApiResponse.success(salesColorService.listSalesColors(includeInactive)));
  }
}
