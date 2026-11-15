package com.fabricmanagement.platform.tradingpartner.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerChequeDrawerService;
import com.fabricmanagement.platform.tradingpartner.dto.SaveTradingPartnerChequeDrawerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerChequeDrawerAddResultDto;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerChequeDrawerDto;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer page's cheque-drawer list (PAYMENT-PLAN-7 R15, R18). Changes follow the same rule as
 * editing the customer itself; the role-named guard is the partner module's current model and moves
 * with it (TD-35). Finance receives drawers through its own endpoints.
 */
@RestController
@RequestMapping("/api/v1/common/trading-partners/{partnerId}/cheque-drawers")
@RequiredArgsConstructor
@Tag(
    name = "Partner Cheque Drawers",
    description = "People and companies who sign a customer's own cheques")
public class TradingPartnerChequeDrawerController {

  private final TradingPartnerChequeDrawerService drawers;

  @GetMapping
  @PreAuthorize(
      "@auth.can(authentication, 'sales', 'read') or @auth.can(authentication, 'finance', 'read')")
  @Operation(
      operationId = "listTradingPartnerChequeDrawers",
      summary = "List a customer's cheque drawers, optionally with deactivated ones")
  public ResponseEntity<ApiResponse<List<TradingPartnerChequeDrawerDto>>> list(
      @PathVariable UUID partnerId, @RequestParam(defaultValue = "false") boolean includeInactive) {
    return ResponseEntity.ok(ApiResponse.success(drawers.list(partnerId, includeInactive)));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(
      operationId = "addTradingPartnerChequeDrawer",
      summary = "Add a cheque drawer; an existing name is returned instead of added twice",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "A new drawer was recorded (outcome CREATED)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "The name already exists: outcome EXISTING or INACTIVE_DUPLICATE"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Customer not found in this tenant"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "CHEQUE_DRAWER_PARTNER_NOT_CUSTOMER or CHEQUE_DRAWER_PARTNER_REMOVED")
      })
  public ResponseEntity<ApiResponse<TradingPartnerChequeDrawerAddResultDto>> add(
      @PathVariable UUID partnerId,
      @Valid @RequestBody SaveTradingPartnerChequeDrawerRequest request) {
    var result = drawers.add(partnerId, request.name(), request.drawerType());
    return ResponseEntity.status(
            result.outcome() == TradingPartnerChequeDrawerAddResultDto.Outcome.CREATED
                ? HttpStatus.CREATED
                : HttpStatus.OK)
        .body(ApiResponse.success(result));
  }

  @PutMapping("/{drawerId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(
      operationId = "changeTradingPartnerChequeDrawer",
      summary = "Rename or retype a cheque drawer; received cheques keep their recorded name",
      responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Renamed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Customer or drawer not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "CHEQUE_DRAWER_NAME_TAKEN: another drawer of this customer has the name")
      })
  public ResponseEntity<ApiResponse<TradingPartnerChequeDrawerDto>> change(
      @PathVariable UUID partnerId,
      @PathVariable UUID drawerId,
      @Valid @RequestBody SaveTradingPartnerChequeDrawerRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            drawers.change(partnerId, drawerId, request.name(), request.drawerType())));
  }

  @PostMapping("/{drawerId}/deactivation")
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(
      operationId = "deactivateTradingPartnerChequeDrawer",
      summary = "Take a cheque drawer out of the picker; nothing is deleted")
  public ResponseEntity<ApiResponse<TradingPartnerChequeDrawerDto>> deactivate(
      @PathVariable UUID partnerId, @PathVariable UUID drawerId) {
    return ResponseEntity.ok(ApiResponse.success(drawers.deactivate(partnerId, drawerId)));
  }

  @PostMapping("/{drawerId}/reactivation")
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(
      operationId = "reactivateTradingPartnerChequeDrawer",
      summary = "Return a deactivated cheque drawer to the picker")
  public ResponseEntity<ApiResponse<TradingPartnerChequeDrawerDto>> reactivate(
      @PathVariable UUID partnerId, @PathVariable UUID drawerId) {
    return ResponseEntity.ok(ApiResponse.success(drawers.reactivate(partnerId, drawerId)));
  }
}
