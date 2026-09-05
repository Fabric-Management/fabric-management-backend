package com.fabricmanagement.product.yarn.app.port;

import java.util.UUID;

public interface YarnUsageSignalSource {
  int MOVEMENT_WINDOW_DAYS = 90;

  YarnUsageDiscovery discover(UUID tenantId);
}
