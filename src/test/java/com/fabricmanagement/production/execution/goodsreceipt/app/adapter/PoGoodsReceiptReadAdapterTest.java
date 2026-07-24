package com.fabricmanagement.production.execution.goodsreceipt.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptStatus;
import com.fabricmanagement.production.execution.goodsreceipt.infra.repository.GoodsReceiptItemRepository;
import com.fabricmanagement.production.execution.goodsreceipt.infra.repository.GoodsReceiptItemRepository.PoReceiptMeasureBucket;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PoGoodsReceiptReadAdapterTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID PO_ID = UUID.randomUUID();
  private static final UUID LINE_ID = UUID.randomUUID();

  @Mock private GoodsReceiptItemRepository itemRepository;

  @Test
  void convertsWeightFromContractualKgIntoRequestedUnits() {
    PoReceiptMeasureBucket weightBucket = bucket(LINE_ID, null, "2.500", null, 2, 2, 0);
    when(itemRepository.sumConfirmedPoReceiptMeasures(
            TENANT_ID, GoodsReceiptSourceType.PURCHASE_ORDER, PO_ID, GoodsReceiptStatus.CONFIRMED))
        .thenReturn(List.of(weightBucket));
    PoGoodsReceiptReadAdapter adapter = new PoGoodsReceiptReadAdapter(itemRepository);

    var kgResult =
        adapter
            .sumReceivedByLine(TENANT_ID, PO_ID, Map.of(LINE_ID, "KG"))
            .receivedByLine()
            .get(LINE_ID);
    assertThat(kgResult.receivedQty()).isEqualByComparingTo("2.500");
    assertThat(kgResult.excludedItemCount()).isZero();
    assertThat(kgResult.receiveMismatch()).isFalse();
    assertThat(
            adapter
                .sumReceivedByLine(TENANT_ID, PO_ID, Map.of(LINE_ID, "G"))
                .receivedByLine()
                .get(LINE_ID)
                .receivedQty())
        .isEqualByComparingTo("2500");
    assertThat(
            adapter
                .sumReceivedByLine(TENANT_ID, PO_ID, Map.of(LINE_ID, "MT"))
                .receivedByLine()
                .get(LINE_ID)
                .receivedQty())
        .isEqualByComparingTo("0.0025");
  }

  @Test
  void convertsMixedLengthBucketsAndCountsExcludedItems() {
    UUID meterLineId = UUID.randomUUID();
    UUID millimeterLineId = UUID.randomUUID();
    List<PoReceiptMeasureBucket> lengthBuckets =
        Stream.of(
                mixedLengthBuckets(LINE_ID),
                mixedLengthBuckets(meterLineId),
                mixedLengthBuckets(millimeterLineId))
            .flatMap(List::stream)
            .toList();
    when(itemRepository.sumConfirmedPoReceiptMeasures(
            TENANT_ID, GoodsReceiptSourceType.PURCHASE_ORDER, PO_ID, GoodsReceiptStatus.CONFIRMED))
        .thenReturn(lengthBuckets);

    var totals =
        new PoGoodsReceiptReadAdapter(itemRepository)
            .sumReceivedByLine(
                TENANT_ID, PO_ID, Map.of(LINE_ID, "CM", meterLineId, "M", millimeterLineId, "MM"))
            .receivedByLine();
    var result = totals.get(LINE_ID);
    var meters = totals.get(meterLineId);
    var millimeters = totals.get(millimeterLineId);

    assertThat(result.receivedQty()).isEqualByComparingTo("250");
    assertThat(meters.receivedQty()).isEqualByComparingTo("2.5");
    assertThat(millimeters.receivedQty()).isEqualByComparingTo("2500");
    assertThat(result.excludedItemCount()).isEqualTo(2);
    assertThat(result.receiveMismatch()).isTrue();
    verify(itemRepository)
        .sumConfirmedPoReceiptMeasures(
            TENANT_ID, GoodsReceiptSourceType.PURCHASE_ORDER, PO_ID, GoodsReceiptStatus.CONFIRMED);
  }

  @Test
  void nullWeightMeasuresAreExcludedAndFlagged() {
    PoReceiptMeasureBucket weightBucket = bucket(LINE_ID, null, "2.500", null, 3, 2, 0);
    when(itemRepository.sumConfirmedPoReceiptMeasures(
            TENANT_ID, GoodsReceiptSourceType.PURCHASE_ORDER, PO_ID, GoodsReceiptStatus.CONFIRMED))
        .thenReturn(List.of(weightBucket));

    var result =
        new PoGoodsReceiptReadAdapter(itemRepository)
            .sumReceivedByLine(TENANT_ID, PO_ID, Map.of(LINE_ID, "KG"))
            .receivedByLine()
            .get(LINE_ID);

    assertThat(result.receivedQty()).isEqualByComparingTo("2.500");
    assertThat(result.excludedItemCount()).isEqualTo(1);
    assertThat(result.receiveMismatch()).isTrue();
  }

  @Test
  void unsupportedLineUnitReturnsZeroAndFlagsEveryItem() {
    PoReceiptMeasureBucket unsupportedBucket = bucket(LINE_ID, null, "12", null, 3, 3, 0);
    when(itemRepository.sumConfirmedPoReceiptMeasures(
            TENANT_ID, GoodsReceiptSourceType.PURCHASE_ORDER, PO_ID, GoodsReceiptStatus.CONFIRMED))
        .thenReturn(List.of(unsupportedBucket));

    var totals =
        new PoGoodsReceiptReadAdapter(itemRepository)
            .sumReceivedByLine(TENANT_ID, PO_ID, Map.of(LINE_ID, "PCS"));
    var result = totals.receivedByLine().get(LINE_ID);

    assertThat(totals.hasConfirmedReceipts()).isTrue();
    assertThat(result.receivedQty()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.excludedItemCount()).isEqualTo(3);
    assertThat(result.unitSupported()).isFalse();
    assertThat(result.receiveMismatch()).isTrue();
  }

  private List<PoReceiptMeasureBucket> mixedLengthBuckets(UUID lineId) {
    return List.of(
        bucket(lineId, "M", "3", "2", 1, 1, 1),
        bucket(lineId, "CM", "4", "50", 2, 2, 1),
        bucket(lineId, "FT", "5", "10", 1, 1, 1));
  }

  private PoReceiptMeasureBucket bucket(
      UUID lineId,
      String lengthUnit,
      String netWeightTotal,
      String lengthTotal,
      long itemCount,
      long netWeightItemCount,
      long lengthItemCount) {
    PoReceiptMeasureBucket bucket = mock(PoReceiptMeasureBucket.class);
    lenient().when(bucket.getSourceLineId()).thenReturn(lineId);
    lenient().when(bucket.getLengthUnit()).thenReturn(lengthUnit);
    lenient()
        .when(bucket.getNetWeightTotal())
        .thenReturn(netWeightTotal != null ? new BigDecimal(netWeightTotal) : null);
    lenient()
        .when(bucket.getLengthTotal())
        .thenReturn(lengthTotal != null ? new BigDecimal(lengthTotal) : null);
    lenient().when(bucket.getItemCount()).thenReturn(itemCount);
    lenient().when(bucket.getNetWeightItemCount()).thenReturn(netWeightItemCount);
    lenient().when(bucket.getLengthMeasureItemCount()).thenReturn(lengthItemCount);
    return bucket;
  }
}
