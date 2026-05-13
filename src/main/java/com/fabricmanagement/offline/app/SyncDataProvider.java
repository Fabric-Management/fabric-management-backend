package com.fabricmanagement.offline.app;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Provider interface for offline synchronization. Each module that needs to send offline data to
 * mobile devices must implement this interface. SyncService will automatically discover and invoke
 * all data providers during a pull.
 */
public interface SyncDataProvider {

  /** The distinct entity type name for the payload map keys (e.g. "TASK", "PRODUCT") */
  String entityType();

  /**
   * Fetch entities modified or created since the given timestamp.
   *
   * @param tenantId The current tenant ID requesting the sync
   * @param since The mobile device's last successful sync timestamp (if null, fetch all to
   *     baseline)
   * @param limit Maximum records to return (for pagination or boundary limitations)
   * @return List of DTOs ready to be serialized to the mobile client
   */
  List<?> pullData(UUID tenantId, Instant since, int limit);
}
