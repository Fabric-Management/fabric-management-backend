package com.fabricmanagement.production.execution.goodsreceipt.app.adapter;

import com.fabricmanagement.procurement.purchaseorder.app.port.PoGoodsReceiptReadPort;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptStatus;
import com.fabricmanagement.production.execution.goodsreceipt.infra.repository.GoodsReceiptItemRepository;
import com.fabricmanagement.production.execution.goodsreceipt.infra.repository.GoodsReceiptItemRepository.PoReceiptMeasureBucket;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Goods-receipt adapter that converts physical receipt measures into each PO line's unit. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PoGoodsReceiptReadAdapter implements PoGoodsReceiptReadPort {

  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
  private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");
  private static final Set<String> WEIGHT_UNITS = Set.of("KG", "G", "MT");
  private static final Set<String> LENGTH_UNITS = Set.of("M", "CM", "MM");

  private final GoodsReceiptItemRepository goodsReceiptItemRepository;

  @Override
  public PoReceiptTotals sumReceivedByLine(
      UUID tenantId, UUID purchaseOrderId, Map<UUID, String> lineUnits) {
    List<PoReceiptMeasureBucket> buckets =
        goodsReceiptItemRepository.sumConfirmedPoReceiptMeasures(
            tenantId,
            GoodsReceiptSourceType.PURCHASE_ORDER,
            purchaseOrderId,
            GoodsReceiptStatus.CONFIRMED);

    Map<UUID, List<PoReceiptMeasureBucket>> bucketsByLine =
        buckets.stream()
            .filter(bucket -> bucket.getSourceLineId() != null)
            .collect(
                java.util.stream.Collectors.groupingBy(PoReceiptMeasureBucket::getSourceLineId));

    long orphanItemCount =
        buckets.stream()
            .filter(bucket -> bucket.getSourceLineId() == null)
            .mapToLong(PoReceiptMeasureBucket::getItemCount)
            .sum();
    if (orphanItemCount > 0) {
      log.warn(
          "Ignoring {} confirmed PO receipt items without sourceLineId: tenant={}, po={}",
          orphanItemCount,
          tenantId,
          purchaseOrderId);
    }

    Map<UUID, LineReceiptTotal> totals = new LinkedHashMap<>();
    lineUnits.forEach(
        (lineId, lineUnit) ->
            totals.put(
                lineId,
                calculateLineTotal(
                    normalizeUnit(lineUnit), bucketsByLine.getOrDefault(lineId, List.of()))));

    bucketsByLine.keySet().stream()
        .filter(lineId -> !lineUnits.containsKey(lineId))
        .forEach(
            lineId ->
                log.warn(
                    "Ignoring confirmed receipt measures for inactive/unknown PO line: tenant={}, po={}, line={}",
                    tenantId,
                    purchaseOrderId,
                    lineId));

    return new PoReceiptTotals(!buckets.isEmpty(), totals);
  }

  private LineReceiptTotal calculateLineTotal(
      String targetUnit, List<PoReceiptMeasureBucket> buckets) {
    if (WEIGHT_UNITS.contains(targetUnit)) {
      BigDecimal totalKg =
          buckets.stream()
              .map(PoReceiptMeasureBucket::getNetWeightTotal)
              .filter(java.util.Objects::nonNull)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      int excluded =
          buckets.stream()
              .mapToInt(
                  bucket -> Math.toIntExact(bucket.getItemCount() - bucket.getNetWeightItemCount()))
              .sum();
      return new LineReceiptTotal(convertWeightFromKg(totalKg, targetUnit), excluded, true);
    }

    if (LENGTH_UNITS.contains(targetUnit)) {
      BigDecimal total = BigDecimal.ZERO;
      int excluded = 0;
      for (PoReceiptMeasureBucket bucket : buckets) {
        String sourceUnit = normalizeUnit(bucket.getLengthUnit());
        if (!LENGTH_UNITS.contains(sourceUnit)) {
          excluded += Math.toIntExact(bucket.getItemCount());
          continue;
        }
        excluded += Math.toIntExact(bucket.getItemCount() - bucket.getLengthMeasureItemCount());
        if (bucket.getLengthTotal() != null) {
          total = total.add(convertLength(bucket.getLengthTotal(), sourceUnit, targetUnit));
        }
      }
      return new LineReceiptTotal(total, excluded, true);
    }

    int excluded =
        buckets.stream().mapToInt(bucket -> Math.toIntExact(bucket.getItemCount())).sum();
    return new LineReceiptTotal(BigDecimal.ZERO, excluded, false);
  }

  private BigDecimal convertWeightFromKg(BigDecimal quantityKg, String targetUnit) {
    return switch (targetUnit) {
      case "KG" -> quantityKg;
      case "G" -> quantityKg.multiply(ONE_THOUSAND);
      case "MT" -> quantityKg.divide(ONE_THOUSAND);
      default -> throw new IllegalArgumentException("Unsupported weight unit: " + targetUnit);
    };
  }

  private BigDecimal convertLength(BigDecimal quantity, String sourceUnit, String targetUnit) {
    BigDecimal meters =
        switch (sourceUnit) {
          case "M" -> quantity;
          case "CM" -> quantity.divide(ONE_HUNDRED);
          case "MM" -> quantity.divide(ONE_THOUSAND);
          default -> throw new IllegalArgumentException("Unsupported length unit: " + sourceUnit);
        };
    return switch (targetUnit) {
      case "M" -> meters;
      case "CM" -> meters.multiply(ONE_HUNDRED);
      case "MM" -> meters.multiply(ONE_THOUSAND);
      default -> throw new IllegalArgumentException("Unsupported length unit: " + targetUnit);
    };
  }

  private String normalizeUnit(String unit) {
    return unit == null ? "" : unit.trim().toUpperCase(Locale.ROOT);
  }
}
