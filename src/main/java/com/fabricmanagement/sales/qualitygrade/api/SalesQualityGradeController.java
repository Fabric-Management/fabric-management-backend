package com.fabricmanagement.sales.qualitygrade.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.qualitygrade.app.SalesQualityGradeService;
import com.fabricmanagement.sales.qualitygrade.dto.SalesQualityGradeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
@RequestMapping("/api/v1/sales/quality-grades")
@RequiredArgsConstructor
@Tag(name = "Sales Quality Grade", description = "Sales-readable quality grade projection")
public class SalesQualityGradeController {

  private final SalesQualityGradeService salesQualityGradeService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "List sales-readable quality grades")
  public ResponseEntity<ApiResponse<List<SalesQualityGradeDto>>> listSalesGrades(
      @Parameter(
              schema =
                  @Schema(allowableValues = {"FIBER", "YARN", "FABRIC", "CHEMICAL", "CONSUMABLE"}))
          @RequestParam
          String productType,
      @RequestParam(defaultValue = "false") boolean includeNonSaleable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            salesQualityGradeService.listSalesGrades(productType, includeNonSaleable)));
  }
}
