package com.fabricmanagement.iwm.reservation.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.iwm.common.exception.IwmDomainException;
import com.fabricmanagement.iwm.reservation.domain.StockReservation;
import com.fabricmanagement.iwm.reservation.domain.event.ReservationConvertedEvent;
import com.fabricmanagement.iwm.reservation.domain.event.ReservationCreatedEvent;
import com.fabricmanagement.iwm.reservation.domain.event.ReservationReleasedEvent;
import com.fabricmanagement.iwm.reservation.dto.LotSuggestion;
import com.fabricmanagement.iwm.reservation.dto.StockReservationResponse;
import com.fabricmanagement.iwm.reservation.infra.repository.StockReservationRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockReservationService {

  private final StockReservationRepository repository;
  private final StockReservationEngine engine;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public List<LotSuggestion> getFifoSuggestions(UUID productId, BigDecimal requiredQty) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return engine.suggestLotsFifo(tenantId, productId, requiredQty);
  }

  @Transactional
  public StockReservationResponse createReservation(
      UUID salesOrderLineId,
      UUID locationId,
      UUID productId,
      String lotNumber,
      UUID goodsReceiptItemId,
      BigDecimal qtyReserved) {

    UUID tenantId = TenantContext.getCurrentTenantId();

    StockReservation reservation =
        new StockReservation(
            tenantId,
            salesOrderLineId,
            locationId,
            productId,
            lotNumber,
            goodsReceiptItemId,
            qtyReserved,
            null);

    StockReservation saved = repository.save(reservation);

    eventPublisher.publishEvent(
        new ReservationCreatedEvent(
            tenantId, saved.getId(), productId, locationId, lotNumber, qtyReserved));

    return toResponse(saved);
  }

  @Transactional
  public void releaseReservation(UUID reservationId) {
    StockReservation reservation = getById(reservationId);
    reservation.release();
    repository.save(reservation);

    eventPublisher.publishEvent(
        new ReservationReleasedEvent(
            reservation.getTenantId(),
            reservationId,
            reservation.getProductId(),
            reservation.getLocationId(),
            reservation.getQtyReserved()));
  }

  @Transactional
  public void convertReservation(UUID reservationId) {
    StockReservation reservation = getById(reservationId);
    reservation.convert();
    repository.save(reservation);

    eventPublisher.publishEvent(
        new ReservationConvertedEvent(
            reservation.getTenantId(),
            reservationId,
            reservation.getProductId(),
            reservation.getLocationId(),
            reservation.getQtyReserved()));
  }

  @Transactional(readOnly = true)
  public StockReservation getById(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return repository
        .findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
        .orElseThrow(() -> new IwmDomainException("StockReservation not found with id: " + id));
  }

  private StockReservationResponse toResponse(StockReservation entity) {
    return StockReservationResponse.builder()
        .id(entity.getId())
        .salesOrderLineId(entity.getSalesOrderLineId())
        .locationId(entity.getLocationId())
        .productId(entity.getProductId())
        .lotNumber(entity.getLotNumber())
        .goodsReceiptItemId(entity.getGoodsReceiptItemId())
        .qtyReserved(entity.getQtyReserved())
        .status(entity.getStatus())
        .expiresAt(entity.getExpiresAt())
        .createdAt(
            entity.getCreatedAt() != null
                ? entity.getCreatedAt().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now())
        .build();
  }
}
