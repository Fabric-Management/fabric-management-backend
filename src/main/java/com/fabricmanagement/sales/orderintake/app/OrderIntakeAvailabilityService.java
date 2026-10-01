package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeAvailabilityDto;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Colour-level stock for the order form (SOI IK-05): what a product has in a colour before a line
 * is saved. Reads the piece-level proposal source that {@link QuantityEvaluationService} works
 * from, so the form and the later evaluation agree. Reserves nothing and decides nothing — a colour
 * with no stock is still a valid line.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderIntakeAvailabilityService {

  private final ProductSalesDefinitionQueryService productDefinitions;
  private final ProposalStockQueryService stockQuery;

  /**
   * @param colorId colour card to match; {@code null} matches colourless lots only (fibre, or stock
   *     whose colour is not an axis)
   * @param customerId optional customer, so customer-confirmed lot compatibility is honoured the
   *     same way the evaluation honours it
   */
  public OrderIntakeAvailabilityDto forProductColour(
      UUID productId, UUID colorId, UUID customerId) {
    UUID tenantId = TenantContext.requireTenantId();
    ProductSalesDefinitionDto product =
        productDefinitions
            .find(tenantId, productId)
            .orElseThrow(() -> OrderIntakeException.productNotAvailable(productId));
    PrimaryMeasure measure =
        stockQuery
            .measureFor(product.productType())
            .orElseThrow(() -> OrderIntakeException.productTypeNotSellable(product.productType()));
    ProposalStockQueryService.ProposalStock stock =
        stockQuery.find(
            new ProposalStockQueryService.ProposalStockQuery(
                tenantId, productId, colorId, null, null, customerId));

    // A piece without an exact canonical measure (no convertible length/weight on record) is
    // counted, never summed: a missing number must not read as zero or as a known quantity.
    BigDecimal available = BigDecimal.ZERO;
    BigDecimal unknown = BigDecimal.ZERO;
    long availablePieces = 0;
    long unknownPieces = 0;
    long unmeasuredPieces = 0;
    for (ProposalLot lot : stock.lots()) {
      for (ProposalPiece piece : lot.pieces()) {
        if (piece.state() == PieceState.EXCLUDED) {
          continue;
        }
        boolean measured = piece.canonicalMeasure() != null;
        if (!measured) {
          unmeasuredPieces++;
        }
        if (piece.state() == PieceState.ELIGIBLE) {
          availablePieces++;
          if (measured) {
            available = available.add(piece.canonicalMeasure());
          }
        } else {
          unknownPieces++;
          if (measured) {
            unknown = unknown.add(piece.canonicalMeasure());
          }
        }
      }
    }
    return new OrderIntakeAvailabilityDto(
        productId,
        colorId,
        stockQuery.canonicalUnit(measure),
        available,
        unknown,
        stock.lots().size(),
        availablePieces,
        unknownPieces,
        unmeasuredPieces);
  }
}
