package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalStock;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.WidthEvidence;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeAvailabilityDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderIntakeAvailabilityServiceTest {

  private final UUID tenantId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final UUID colorId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();

  @Mock private ProductSalesDefinitionQueryService productDefinitions;
  @Mock private ProposalStockQueryService stockQuery;

  private OrderIntakeAvailabilityService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    service = new OrderIntakeAvailabilityService(productDefinitions, stockQuery);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void sumsEligiblePiecesAndKeepsUnknownOnesApart_inTheCanonicalUnit() {
    when(productDefinitions.find(tenantId, productId))
        .thenReturn(Optional.of(definition(ProductType.FABRIC)));
    when(stockQuery.measureFor(ProductType.FABRIC)).thenReturn(Optional.of(PrimaryMeasure.LENGTH));
    when(stockQuery.canonicalUnit(PrimaryMeasure.LENGTH)).thenReturn("M");
    when(stockQuery.find(any()))
        .thenReturn(
            new ProposalStock(
                List.of(
                    lot(
                        "LOT-24011",
                        piece("125", PieceState.ELIGIBLE),
                        piece("125", PieceState.ELIGIBLE),
                        piece("60", PieceState.EXCLUDED)),
                    lot(
                        "LOT-24020",
                        piece("25", PieceState.UNKNOWN),
                        // No exact canonical measure on record: counted, never summed.
                        unmeasured(PieceState.UNKNOWN),
                        unmeasured(PieceState.ELIGIBLE))),
                List.of()));

    OrderIntakeAvailabilityDto result = service.forProductColour(productId, colorId, customerId);

    assertThat(result.unit()).isEqualTo("M");
    assertThat(result.availableQuantity()).isEqualByComparingTo("250");
    assertThat(result.unknownQuantity()).isEqualByComparingTo("25");
    assertThat(result.lotCount()).isEqualTo(2);
    assertThat(result.availablePieceCount()).isEqualTo(3);
    assertThat(result.unknownPieceCount()).isEqualTo(2);
    assertThat(result.unmeasuredPieceCount()).isEqualTo(2);
    assertThat(result.colorId()).isEqualTo(colorId);

    // The query carries product, colour and customer through unchanged and asks for no width, so
    // width evidence never hides a lot from the form's advisory number.
    ArgumentCaptor<ProposalStockQueryService.ProposalStockQuery> query =
        ArgumentCaptor.forClass(ProposalStockQueryService.ProposalStockQuery.class);
    verify(stockQuery).find(query.capture());
    assertThat(query.getValue().tenantId()).isEqualTo(tenantId);
    assertThat(query.getValue().productId()).isEqualTo(productId);
    assertThat(query.getValue().colorId()).isEqualTo(colorId);
    assertThat(query.getValue().customerId()).isEqualTo(customerId);
    assertThat(query.getValue().finishedWidth()).isNull();
  }

  @Test
  void zeroStockIsAnHonestZero_notAnError() {
    when(productDefinitions.find(tenantId, productId))
        .thenReturn(Optional.of(definition(ProductType.YARN)));
    when(stockQuery.measureFor(ProductType.YARN)).thenReturn(Optional.of(PrimaryMeasure.WEIGHT));
    when(stockQuery.canonicalUnit(PrimaryMeasure.WEIGHT)).thenReturn("KG");
    when(stockQuery.find(any())).thenReturn(new ProposalStock(List.of(), List.of()));

    OrderIntakeAvailabilityDto result = service.forProductColour(productId, colorId, null);

    assertThat(result.unit()).isEqualTo("KG");
    assertThat(result.availableQuantity()).isEqualByComparingTo("0");
    assertThat(result.unknownQuantity()).isEqualByComparingTo("0");
    assertThat(result.lotCount()).isZero();
  }

  @Test
  void unknownProductIsRefused_beforeAnyStockIsRead() {
    when(productDefinitions.find(tenantId, productId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.forProductColour(productId, colorId, null))
        .isInstanceOf(OrderIntakeException.class);
    verify(stockQuery, never()).find(any());
  }

  private ProductSalesDefinitionDto definition(ProductType type) {
    return new ProductSalesDefinitionDto(
        productId,
        "PRD-1",
        "Demo product",
        type,
        type == ProductType.FABRIC ? "M" : "KG",
        true,
        List.of(),
        List.of());
  }

  private static ProposalLot lot(String lotNo, ProposalPiece... pieces) {
    return new ProposalLot(
        UUID.randomUUID(),
        lotNo,
        Instant.parse("2026-07-01T00:00:00Z"),
        PrimaryMeasure.LENGTH,
        "M",
        WidthEvidence.NOT_REQUIRED,
        List.of(pieces));
  }

  private static ProposalPiece piece(String measure, PieceState state) {
    return new ProposalPiece(
        UUID.randomUUID(), "P-" + measure, new BigDecimal(measure), state, null, 0L);
  }

  private static ProposalPiece unmeasured(PieceState state) {
    return new ProposalPiece(UUID.randomUUID(), "P-?", null, state, "no measure", 0L);
  }
}
