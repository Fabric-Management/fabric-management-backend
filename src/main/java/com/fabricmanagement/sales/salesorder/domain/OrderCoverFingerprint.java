package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import java.util.Set;

/** Evidence identity excludes read-observation time; canonical hashing is shared infrastructure. */
public final class OrderCoverFingerprint {
  private OrderCoverFingerprint() {}

  public static String of(Object value) {
    return CanonicalJsonFingerprint.of(value, Set.of("observedAt"));
  }
}
