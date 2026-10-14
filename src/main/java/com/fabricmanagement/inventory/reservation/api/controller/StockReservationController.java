package com.fabricmanagement.inventory.reservation.api.controller;

import com.fabricmanagement.inventory.reservation.app.StockReservationService;
import com.fabricmanagement.inventory.reservation.dto.CreateReservationRequest;
import com.fabricmanagement.inventory.reservation.dto.StockReservationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/iwm/reservations")
@RequiredArgsConstructor
@Tag(name = "IWM Stock Reservation", description = "Stock Reservation API")
@Slf4j
public class StockReservationController {

  private final StockReservationService reservationService;

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
