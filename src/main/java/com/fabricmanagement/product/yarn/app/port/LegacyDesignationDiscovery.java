package com.fabricmanagement.product.yarn.app.port;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Bulk discovery result, including evidence whose product linkage could not be resolved. */
public record LegacyDesignationDiscovery(
    List<LegacyDesignationRecord> records, Map<LegacyDesignationSourceKind, Long> unlinkedCounts) {

  public LegacyDesignationDiscovery {
    records = records == null ? List.of() : List.copyOf(records);
    EnumMap<LegacyDesignationSourceKind, Long> counts =
        new EnumMap<>(LegacyDesignationSourceKind.class);
    if (unlinkedCounts != null) {
      counts.putAll(unlinkedCounts);
    }
    unlinkedCounts = Map.copyOf(counts);
  }

  public static LegacyDesignationDiscovery empty() {
    return new LegacyDesignationDiscovery(List.of(), Map.of());
  }
}
