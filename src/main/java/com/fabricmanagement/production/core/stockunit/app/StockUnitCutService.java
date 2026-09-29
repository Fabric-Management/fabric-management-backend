package com.fabricmanagement.production.core.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.core.stockunit.domain.StockUnit;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCut;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutDtos;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitCutRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records cuts taken from pieces (SOI A06). The stated remaining length becomes the piece length
 * only when someone verifies it; until then the piece stays out of whole-piece proposals.
 */
@Service
@RequiredArgsConstructor
public class StockUnitCutService {

  private final StockUnitRepository stockUnitRepository;
  private final StockUnitCutRepository cutRepository;
  private final Clock clock;

  @Transactional
  public StockUnitCutDtos.CutDto recordCut(
      UUID stockUnitId, StockUnitCutDtos.RecordCutRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    StockUnit unit = unit(tenantId, stockUnitId);
    if (unit.getLength() == null || unit.getLengthUnit() == null) {
      throw new IllegalArgumentException("Only pieces with a recorded length can be cut by length");
    }
    if (unit.getStatus() != StockUnitStatus.AVAILABLE
        && unit.getStatus() != StockUnitStatus.PARTIAL) {
      throw new IllegalArgumentException(
          "A piece in status " + unit.getStatus() + " cannot be cut");
    }
    if (request.cutLength().add(request.remainingLength()).compareTo(unit.getLength()) > 0) {
      throw new IllegalArgumentException(
          "Cut and remaining length exceed the recorded piece length");
    }
    StockUnitCut cut =
        cutRepository.save(
            StockUnitCut.record(
                stockUnitId,
                request.cutLength(),
                request.remainingLength(),
                unit.getLengthUnit(),
                actor(),
                Instant.now(clock)));
    return toDto(cut);
  }

  /** Verifies the remaining length of a cut and records it as the piece's length. */
  @Transactional
  public StockUnitCutDtos.CutDto verifyRemaining(UUID stockUnitId, UUID cutId) {
    UUID tenantId = TenantContext.requireTenantId();
    StockUnitCut cut =
        cutRepository
            .findByTenantIdAndIdAndStockUnitId(tenantId, cutId, stockUnitId)
            .orElseThrow(() -> new NotFoundException("Cut not found: " + cutId));
    StockUnit unit = unit(tenantId, stockUnitId);
    cut.verifyRemaining(actor(), Instant.now(clock));
    if (cut.getRemainingLength().signum() > 0) {
      unit.recordLength(cut.getRemainingLength(), cut.getLengthUnit());
      stockUnitRepository.save(unit);
    }
    return toDto(cutRepository.save(cut));
  }

  private StockUnit unit(UUID tenantId, UUID stockUnitId) {
    return stockUnitRepository
        .findByIdAndTenantIdAndIsActiveTrue(stockUnitId, tenantId)
        .orElseThrow(() -> new NotFoundException("Stock unit not found: " + stockUnitId));
  }

  private static UUID actor() {
    UUID actor = TenantContext.getCurrentUserId();
    if (actor == null) {
      throw new IllegalStateException("An authenticated actor is required for piece cuts");
    }
    return actor;
  }

  static StockUnitCutDtos.CutDto toDto(StockUnitCut cut) {
    return new StockUnitCutDtos.CutDto(
        cut.getId(),
        cut.getStockUnitId(),
        cut.getCutLength(),
        cut.getRemainingLength(),
        cut.getLengthUnit(),
        cut.getRecordedBy(),
        cut.getRecordedAt(),
        cut.getRemainingVerifiedBy(),
        cut.getRemainingVerifiedAt());
  }
}
