package com.fabricmanagement.product.yarn.app.backfill;

import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueReason;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public record YarnLegacyBackfillReport(
    UUID tenantId,
    YarnLegacyBackfillOutcome outcome,
    long productsScanned,
    long productsSkipped,
    long articlesCreated,
    long candidatesWritten,
    Map<YarnBackfillQueueReason, Long> queueRowsCreated,
    Map<LegacyDesignationSourceKind, Long> recordsContributed,
    Map<LegacyDesignationSourceKind, Long> recordsWithoutLinkage) {

  public YarnLegacyBackfillReport {
    queueRowsCreated = immutableEnumMap(YarnBackfillQueueReason.class, queueRowsCreated);
    recordsContributed = immutableEnumMap(LegacyDesignationSourceKind.class, recordsContributed);
    recordsWithoutLinkage =
        immutableEnumMap(LegacyDesignationSourceKind.class, recordsWithoutLinkage);
  }

  public long totalQueueRowsCreated() {
    return queueRowsCreated.values().stream().mapToLong(Long::longValue).sum();
  }

  public static YarnLegacyBackfillReport lockSkipped(UUID tenantId) {
    return new YarnLegacyBackfillReport(
        tenantId, YarnLegacyBackfillOutcome.LOCK_SKIPPED, 0, 0, 0, 0, Map.of(), Map.of(), Map.of());
  }

  private static <E extends Enum<E>> Map<E, Long> immutableEnumMap(
      Class<E> enumType, Map<E, Long> source) {
    EnumMap<E, Long> copy = new EnumMap<>(enumType);
    for (E value : enumType.getEnumConstants()) {
      copy.put(value, 0L);
    }
    if (source != null) {
      copy.putAll(source);
    }
    return Map.copyOf(copy);
  }
}
