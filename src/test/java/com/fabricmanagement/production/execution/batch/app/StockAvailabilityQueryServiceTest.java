package com.fabricmanagement.production.execution.batch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.BatchUnitMismatchSource;
import com.fabricmanagement.production.execution.batch.domain.exception.StockAvailabilityFilterException;
import com.fabricmanagement.production.execution.batch.dto.StockAvailabilityDtos.PhysicalSource;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.StockAvailabilityBatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.production.masterdata.qualitygrade.api.query.QualityGradeQueryService.QualityGradeReference;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class StockAvailabilityQueryServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final UUID COLOR_ID = UUID.randomUUID();
  private static final UUID GRADE_ONE = UUID.randomUUID();
  private static final UUID GRADE_TWO = UUID.randomUUID();

  @Mock private BatchRepository batchRepository;
  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private BatchCommitmentQuantityService commitmentQuantityService;
  @Mock private QualityGradeQueryService qualityGradeQueryService;

  private StockAvailabilityQueryService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    service =
        new StockAvailabilityQueryService(
            batchRepository,
            stockUnitRepository,
            new BatchPrimaryMeasureService(),
            commitmentQuantityService,
            qualityGradeQueryService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void lotsCanonicalisePhysicalStockAndExposeQualityWithoutQuarantine() {
    Batch batch = batch("LOT-001", "150", "M");
    var pageable = PageRequest.of(0, 20);
    var vectorRows =
        List.of(
            vector(batch, StockUnitStatus.AVAILABLE, GRADE_ONE, "KG", "10", "M", "100", 1),
            vector(batch, StockUnitStatus.AVAILABLE, GRADE_TWO, "LB", "22", "CM", "500", 1),
            vector(batch, StockUnitStatus.AVAILABLE, null, "KG", "5", "M", "20", 1),
            vector(batch, StockUnitStatus.QUARANTINE, GRADE_ONE, "KG", "100", "M", "1000", 1));
    var pieceRows =
        List.of(
            piece(batch, PackageType.ROLL, "KG", "15", "M", "120", 2),
            piece(batch, PackageType.ROLL, "LB", "22", "CM", "500", 1));
    var qualityRows =
        List.of(
            quality(batch, GRADE_ONE, "KG", "10", "M", "100", 1),
            quality(batch, GRADE_TWO, "LB", "22", "CM", "500", 1),
            quality(batch, null, "KG", "5", "M", "20", 1));
    var colourRows = List.of(colour(batch));

    when(batchRepository.findAvailabilityLots(
            eq(TENANT_ID), any(StockAvailabilityBatchRepository.Filter.class), any()))
        .thenReturn(new PageImpl<>(List.of(batch), pageable, 1));
    when(stockUnitRepository.findAvailabilityVectorRows(TENANT_ID, List.of(batch.getId())))
        .thenReturn(vectorRows);
    when(stockUnitRepository.findAvailabilityPieceBreakdownRows(
            eq(TENANT_ID), eq(List.of(batch.getId())), any(), eq(null), eq(false)))
        .thenReturn(pieceRows);
    when(stockUnitRepository.findAvailabilityQualityBreakdownRows(
            eq(TENANT_ID), eq(List.of(batch.getId())), any(), eq(null), eq(false)))
        .thenReturn(qualityRows);
    when(qualityGradeQueryService.findReferencesByIds(Set.of(GRADE_ONE, GRADE_TWO)))
        .thenReturn(List.of(grade(GRADE_TWO, "2", 2, false), grade(GRADE_ONE, "1", 1, true)));
    when(commitmentQuantityService.summarize(TENANT_ID, List.of(batch), null))
        .thenReturn(Map.of(batch.getId(), commitments("10", "5")));
    when(batchRepository.findColorReferencesByBatchIds(TENANT_ID, List.of(batch.getId())))
        .thenReturn(colourRows);

    var result = service.lots(COLOR_ID, null, null, null, null, null, pageable);
    var lot = result.getContent().getFirst();

    assertThat(lot.physicalSource()).isEqualTo(PhysicalSource.PIECES);
    assertThat(lot.physical().pieceCount()).isEqualTo(3);
    assertThat(lot.physical().kg()).isEqualByComparingTo("15");
    assertThat(lot.physical().metres()).isEqualByComparingTo("125");
    assertThat(lot.free()).isEqualByComparingTo("110");
    assertThat(lot.unitMismatches())
        .anySatisfy(
            mismatch -> {
              assertThat(mismatch.source()).isEqualTo(BatchUnitMismatchSource.PIECE_WEIGHT);
              assertThat(mismatch.unit()).isEqualTo("LB");
              assertThat(mismatch.quantity()).isEqualByComparingTo("22");
            });
    assertThat(lot.qualityBreakdown()).hasSize(3);
    assertThat(lot.qualityBreakdown().get(0).grade().id()).isEqualTo(GRADE_ONE);
    assertThat(lot.qualityBreakdown().get(1).grade().saleable()).isFalse();
    assertThat(lot.qualityBreakdown().get(2).grade()).isNull();
    assertThat(lot.qualityBreakdown().get(2).pieceCount()).isEqualTo(1);
  }

  @Test
  void qualityFilterScopesPhysicalPiecesButKeepsLotGrainCommitmentsAndFree() {
    Batch batch = batch("LOT-001", "150", "M");
    var pageable = PageRequest.of(0, 20);
    var vectorRows =
        List.of(
            vector(batch, StockUnitStatus.AVAILABLE, GRADE_ONE, "KG", "10", "M", "100", 1),
            vector(batch, StockUnitStatus.AVAILABLE, GRADE_TWO, "LB", "22", "CM", "500", 1));
    var pieceRows = List.of(piece(batch, PackageType.ROLL, "LB", "22", "CM", "500", 1));
    var qualityRows = List.of(quality(batch, GRADE_TWO, "LB", "22", "CM", "500", 1));
    var colourRows = List.of(colour(batch));

    when(batchRepository.findAvailabilityLots(
            eq(TENANT_ID), any(StockAvailabilityBatchRepository.Filter.class), any()))
        .thenReturn(new PageImpl<>(List.of(batch), pageable, 1));
    when(stockUnitRepository.findAvailabilityVectorRows(TENANT_ID, List.of(batch.getId())))
        .thenReturn(vectorRows);
    when(stockUnitRepository.findAvailabilityPieceBreakdownRows(
            eq(TENANT_ID), eq(List.of(batch.getId())), any(), eq(GRADE_TWO), eq(false)))
        .thenReturn(pieceRows);
    when(stockUnitRepository.findAvailabilityQualityBreakdownRows(
            eq(TENANT_ID), eq(List.of(batch.getId())), any(), eq(GRADE_TWO), eq(false)))
        .thenReturn(qualityRows);
    when(qualityGradeQueryService.findReferencesByIds(Set.of(GRADE_TWO)))
        .thenReturn(List.of(grade(GRADE_TWO, "2", 2, false)));
    when(commitmentQuantityService.summarize(TENANT_ID, List.of(batch), null))
        .thenReturn(Map.of(batch.getId(), commitments("10", "5")));
    when(batchRepository.findColorReferencesByBatchIds(TENANT_ID, List.of(batch.getId())))
        .thenReturn(colourRows);

    var lot =
        service.lots(COLOR_ID, null, null, null, GRADE_TWO, null, pageable).getContent().getFirst();

    assertThat(lot.physical().pieceCount()).isEqualTo(1);
    assertThat(lot.physical().kg()).isNull();
    assertThat(lot.physical().metres()).isEqualByComparingTo("5");
    assertThat(lot.softIntent()).isEqualByComparingTo("10");
    assertThat(lot.hardReserved()).isEqualByComparingTo("5");
    assertThat(lot.free()).isEqualByComparingTo("90");
    assertThat(lot.qualityBreakdown())
        .singleElement()
        .satisfies(row -> assertThat(row.grade().id()).isEqualTo(GRADE_TWO));
  }

  @Test
  void summaryAggregatesEveryMatchingLotForThePagedProduct() {
    Batch first = batch("LOT-001", "100", "M");
    Batch second = batch("LOT-002", "200", "M");
    var pageable = PageRequest.of(0, 1);
    ProductType type = ProductType.FABRIC;
    when(batchRepository.findAvailabilityProducts(
            eq(TENANT_ID), any(StockAvailabilityBatchRepository.Filter.class), any()))
        .thenReturn(
            new PageImpl<>(
                List.of(new StockAvailabilityBatchRepository.ProductRow(PRODUCT_ID, type)),
                pageable,
                1));
    when(batchRepository.findAvailabilityBatchesForProducts(
            eq(TENANT_ID),
            any(StockAvailabilityBatchRepository.Filter.class),
            eq(List.of(PRODUCT_ID))))
        .thenReturn(List.of(first, second));
    when(stockUnitRepository.findAvailabilityVectorRows(
            TENANT_ID, List.of(first.getId(), second.getId())))
        .thenReturn(List.of());
    when(stockUnitRepository.findAvailabilityPieceBreakdownRows(
            eq(TENANT_ID), any(), any(), eq(null), eq(false)))
        .thenReturn(List.of());
    when(stockUnitRepository.findAvailabilityQualityBreakdownRows(
            eq(TENANT_ID), any(), any(), eq(null), eq(false)))
        .thenReturn(List.of());
    when(qualityGradeQueryService.findReferencesByIds(Set.of())).thenReturn(List.of());
    when(commitmentQuantityService.summarize(TENANT_ID, List.of(first, second), null))
        .thenReturn(
            Map.of(
                first.getId(), commitments("0", "0"),
                second.getId(), commitments("0", "0")));

    var summary =
        service.summary(COLOR_ID, null, null, null, null, null, pageable).getContent().getFirst();

    assertThat(summary.lotCount()).isEqualTo(2);
    assertThat(summary.physical().metres()).isEqualByComparingTo("300");
    assertThat(summary.free()).isEqualByComparingTo("300");
    assertThat(summary.qualityBreakdown()).isEmpty();
    verify(batchRepository)
        .findAvailabilityBatchesForProducts(
            eq(TENANT_ID),
            any(StockAvailabilityBatchRepository.Filter.class),
            eq(List.of(PRODUCT_ID)));
  }

  @Test
  void invalidFilterCombinationsFailWithTheCanonicalCode() {
    assertThatThrownBy(
            () -> service.lots(null, false, null, null, null, false, PageRequest.of(0, 20)))
        .isInstanceOf(StockAvailabilityFilterException.class)
        .extracting(error -> ((StockAvailabilityFilterException) error).getErrorCode())
        .isEqualTo("STOCK_AVAILABILITY_FILTER_INVALID");
    assertThatThrownBy(
            () -> service.summary(COLOR_ID, true, null, null, null, null, PageRequest.of(0, 20)))
        .isInstanceOf(StockAvailabilityFilterException.class);
    assertThatThrownBy(
            () -> service.summary(null, null, null, null, GRADE_ONE, true, PageRequest.of(0, 20)))
        .isInstanceOf(StockAvailabilityFilterException.class);
  }

  private Batch batch(String code, String quantity, String unit) {
    Batch batch =
        Batch.builder()
            .productId(PRODUCT_ID)
            .productType(ProductType.FABRIC)
            .colorId(COLOR_ID)
            .batchCode(code)
            .quantity(new BigDecimal(quantity))
            .consumedQuantity(BigDecimal.ZERO)
            .reservedQuantity(BigDecimal.ZERO)
            .unit(unit)
            .status(BatchStatus.AVAILABLE)
            .build();
    batch.setId(UUID.randomUUID());
    batch.setTenantId(TENANT_ID);
    batch.setIsActive(true);
    return batch;
  }

  private StockUnitRepository.AvailabilityVectorRow vector(
      Batch batch,
      StockUnitStatus status,
      UUID gradeId,
      String weightUnit,
      String weight,
      String lengthUnit,
      String length,
      long count) {
    var row = mock(StockUnitRepository.AvailabilityVectorRow.class);
    lenient().when(row.getBatchId()).thenReturn(batch.getId());
    lenient().when(row.getStatus()).thenReturn(status);
    lenient().when(row.getQualityGradeId()).thenReturn(gradeId);
    lenient().when(row.getWeightUnit()).thenReturn(weightUnit);
    lenient().when(row.getWeightQuantity()).thenReturn(new BigDecimal(weight));
    lenient().when(row.getLengthUnit()).thenReturn(lengthUnit);
    lenient().when(row.getLengthQuantity()).thenReturn(new BigDecimal(length));
    lenient().when(row.getPieceCount()).thenReturn(count);
    return row;
  }

  private StockUnitRepository.AvailabilityPieceBreakdownRow piece(
      Batch batch,
      PackageType packageType,
      String weightUnit,
      String weight,
      String lengthUnit,
      String length,
      long count) {
    var row = mock(StockUnitRepository.AvailabilityPieceBreakdownRow.class);
    lenient().when(row.getBatchId()).thenReturn(batch.getId());
    lenient().when(row.getPackageType()).thenReturn(packageType);
    lenient().when(row.getWeightUnit()).thenReturn(weightUnit);
    lenient().when(row.getWeightQuantity()).thenReturn(new BigDecimal(weight));
    lenient().when(row.getLengthUnit()).thenReturn(lengthUnit);
    lenient().when(row.getLengthQuantity()).thenReturn(new BigDecimal(length));
    lenient().when(row.getPieceCount()).thenReturn(count);
    return row;
  }

  private StockUnitRepository.AvailabilityQualityBreakdownRow quality(
      Batch batch,
      UUID gradeId,
      String weightUnit,
      String weight,
      String lengthUnit,
      String length,
      long count) {
    var row = mock(StockUnitRepository.AvailabilityQualityBreakdownRow.class);
    lenient().when(row.getBatchId()).thenReturn(batch.getId());
    lenient().when(row.getQualityGradeId()).thenReturn(gradeId);
    lenient().when(row.getWeightUnit()).thenReturn(weightUnit);
    lenient().when(row.getWeightQuantity()).thenReturn(new BigDecimal(weight));
    lenient().when(row.getLengthUnit()).thenReturn(lengthUnit);
    lenient().when(row.getLengthQuantity()).thenReturn(new BigDecimal(length));
    lenient().when(row.getPieceCount()).thenReturn(count);
    return row;
  }

  private QualityGradeReference grade(UUID id, String code, int rank, boolean saleable) {
    return new QualityGradeReference(
        id, code, "Grade " + code, rank, BigDecimal.ONE, saleable, true);
  }

  private BatchCommitmentQuantityService.Summary commitments(String soft, String hard) {
    return new BatchCommitmentQuantityService.Summary(
        new BigDecimal(soft), new BigDecimal(hard), List.of());
  }

  private BatchRepository.BatchColorProjection colour(Batch batch) {
    var projection = mock(BatchRepository.BatchColorProjection.class);
    when(projection.getBatchId()).thenReturn(batch.getId());
    when(projection.getColorId()).thenReturn(COLOR_ID);
    when(projection.getColorCode()).thenReturn("NAVY-01");
    when(projection.getColorName()).thenReturn("Navy");
    when(projection.getColorHex()).thenReturn("#001F3F");
    return projection;
  }
}
