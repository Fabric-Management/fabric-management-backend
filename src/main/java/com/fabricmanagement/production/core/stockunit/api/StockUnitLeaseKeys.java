package com.fabricmanagement.production.core.stockunit.api;

import com.fabricmanagement.platform.realtime.domain.LiveLeaseKey;
import com.fabricmanagement.platform.realtime.domain.LiveResource;
import java.util.UUID;

/** One stock identity across quotes, unsaved drafts and orders, within the bound tenant. */
public final class StockUnitLeaseKeys {
  public static final String RESOURCE_TYPE = "stock-product";
  public static final long GENERATION = 1;
  public static final String LIVE_EVENTS_TEMPLATE =
      "/api/v1/sales/order-intake/products/{productId}/stock-live-events";
  private static final String PREFIX = "stock-unit:";

  private StockUnitLeaseKeys() {}

  public static String liveEventsPath(UUID productId) {
    return LIVE_EVENTS_TEMPLATE.replace("{productId}", productId.toString());
  }

  public static LiveResource resource(UUID productId) {
    return new LiveResource(RESOURCE_TYPE, productId);
  }

  public static LiveLeaseKey key(UUID pieceId) {
    return LiveLeaseKey.whole(PREFIX + pieceId);
  }

  public static UUID pieceId(LiveLeaseKey key) {
    if (!key.isWhole() || !key.scope().startsWith(PREFIX)) {
      throw new IllegalArgumentException("Not a stock-unit lease key");
    }
    return UUID.fromString(key.scope().substring(PREFIX.length()));
  }
}
