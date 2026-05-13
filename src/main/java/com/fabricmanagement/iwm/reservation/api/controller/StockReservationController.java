package com.fabricmanagement.iwm.reservation.api.controller;

import com.fabricmanagement.iwm.reservation.app.StockReservationService;
import com.fabricmanagement.iwm.reservation.dto.CreateReservationRequest;
import com.fabricmanagement.iwm.reservation.dto.LotSuggestion;
import com.fabricmanagement.iwm.reservation.dto.StockReservationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iwm/reservations")
@RequiredArgsConstructor
@Tag(name = "IWM Stock Reservation", description = "Stock Reservation and Lot Engine API")
@Slf4j
public class StockReservationController {

  private final StockReservationService reservationService;

  @Operation(summary = "Get FIFO Lot Suggestions")
  @GetMapping("/suggestions/fifo")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<List<LotSuggestion>> getFifoSuggestions(
      @RequestParam UUID productId, @RequestParam BigDecimal requiredQty) {
    return ResponseEntity.ok(reservationService.getFifoSuggestions(productId, requiredQty));
  }

  @Operation(summary = "Create Reservation")
  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  public ResponseEntity<StockReservationResponse> createReservation(
      @RequestBody @Valid CreateReservationRequest request) {
    StockReservationResponse response =
        reservationService.createReservation(
            request.getSalesOrderLineId(),
            request.getLocationId(),
            request.getProductId(),
            request.getLotNumber(),
            request.getGoodsReceiptItemId(),
            request.getQtyReserved());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(summary = "Release Reservation")
  @PostMapping("/{id}/release")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  public ResponseEntity<Void> releaseReservation(@PathVariable UUID id) {
    reservationService.releaseReservation(id);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Convert Reservation to Real Move (Shipment)")
  @PostMapping("/{id}/convert")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  public ResponseEntity<Void> convertReservation(@PathVariable UUID id) {
    reservationService.convertReservation(id);
    return ResponseEntity.ok().build();
  }
}
