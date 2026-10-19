package com.fabricmanagement.sales.orderintake.domain.proposal;

import java.util.Comparator;
import java.util.List;

/** Shared total order for fabric search and card selection; no prefix-equality cycles. */
public record FabricRollRank(
    int physicalRemnants,
    int freeOnlyRemnants,
    long total,
    List<Integer> partialNormals,
    List<Integer> youngestFirst,
    int lots,
    String stableKey) {
  public FabricRollRank {
    partialNormals = List.copyOf(partialNormals);
    youngestFirst = List.copyOf(youngestFirst);
  }

  public static final Comparator<FabricRollRank> COMPLETION =
      Comparator.comparingInt(FabricRollRank::physicalRemnants)
          .reversed()
          .thenComparing(Comparator.comparingInt(FabricRollRank::freeOnlyRemnants).reversed())
          .thenComparingLong(FabricRollRank::total)
          .thenComparing(FabricRollRank::partialNormals, FabricRollRank::compareVector)
          .thenComparing(FabricRollRank::youngestFirst, FabricRollRank::compareVector)
          .thenComparingInt(FabricRollRank::lots)
          .thenComparing(FabricRollRank::stableKey);

  public static final Comparator<FabricRollRank> BELOW =
      Comparator.comparingLong(FabricRollRank::total).reversed().thenComparing(COMPLETION);

  public FabricRollRank scaled(long scale) {
    return new FabricRollRank(
        physicalRemnants,
        freeOnlyRemnants,
        Math.multiplyExact(total, scale),
        partialNormals,
        youngestFirst,
        lots,
        stableKey);
  }

  public static int compareVector(List<Integer> left, List<Integer> right) {
    for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
      int compared = Integer.compare(left.get(i), right.get(i));
      if (compared != 0) return compared;
    }
    return Integer.compare(left.size(), right.size());
  }
}
