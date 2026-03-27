package com.fabricmanagement.platform.tradingpartner.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerCertificationService;
import com.fabricmanagement.platform.tradingpartner.dto.AddTradingPartnerCertificationRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerCertificationDto;
import com.fabricmanagement.platform.tradingpartner.dto.UpdateTradingPartnerCertificationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/trading-partners/{partnerId}/certifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Partner Certifications", description = "Manage certifications for trading partners")
public class PartnerCertificationController {

  private final TradingPartnerCertificationService certificationService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(
      summary = "List partner certifications",
      description = "Lists all active certifications for a trading partner")
  public ResponseEntity<ApiResponse<List<TradingPartnerCertificationDto>>> list(
      @Parameter(description = "Partner ID") @PathVariable UUID partnerId) {
    List<TradingPartnerCertificationDto> list = certificationService.findByPartnerId(partnerId);
    return ResponseEntity.ok(ApiResponse.success(list, "Found " + list.size() + " certifications"));
  }

  @GetMapping("/{certificationId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(
      summary = "Get partner certification",
      description = "Gets a specific certification for a trading partner")
  public ResponseEntity<ApiResponse<TradingPartnerCertificationDto>> get(
      @Parameter(description = "Partner ID") @PathVariable UUID partnerId,
      @Parameter(description = "Certification record ID") @PathVariable UUID certificationId) {
    TradingPartnerCertificationDto dto = certificationService.findById(partnerId, certificationId);
    return ResponseEntity.ok(ApiResponse.success(dto));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(
      summary = "Add certification",
      description = "Adds a certification to a trading partner")
  public ResponseEntity<ApiResponse<TradingPartnerCertificationDto>> add(
      @Parameter(description = "Partner ID") @PathVariable UUID partnerId,
      @Valid @RequestBody AddTradingPartnerCertificationRequest request) {
    TradingPartnerCertificationDto created = certificationService.add(partnerId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(created, "Certification added successfully"));
  }

  @PutMapping("/{certificationId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(summary = "Update certification", description = "Updates a partner certification")
  public ResponseEntity<ApiResponse<TradingPartnerCertificationDto>> update(
      @Parameter(description = "Partner ID") @PathVariable UUID partnerId,
      @Parameter(description = "Certification record ID") @PathVariable UUID certificationId,
      @Valid @RequestBody UpdateTradingPartnerCertificationRequest request) {
    TradingPartnerCertificationDto updated =
        certificationService.update(partnerId, certificationId, request);
    return ResponseEntity.ok(ApiResponse.success(updated, "Certification updated successfully"));
  }

  @DeleteMapping("/{certificationId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(summary = "Delete certification", description = "Soft-deletes a partner certification")
  public ResponseEntity<ApiResponse<Void>> delete(
      @Parameter(description = "Partner ID") @PathVariable UUID partnerId,
      @Parameter(description = "Certification record ID") @PathVariable UUID certificationId) {
    certificationService.delete(partnerId, certificationId);
    return ResponseEntity.ok(ApiResponse.success(null, "Certification deleted successfully"));
  }
}
