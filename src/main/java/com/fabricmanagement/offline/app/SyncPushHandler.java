package com.fabricmanagement.offline.app;

import com.fabricmanagement.offline.dto.SyncPushResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Handler interface for processing offline push payloads from the mobile application. Modules
 * should implement this interface to process creations/updates to their domain entities.
 * SyncService will automatically discover and invoke the matching handler.
 */
public interface SyncPushHandler {

  /** The explicit entity type this handler supports (e.g. "TASK", "PRODUCT_MOVE"). */
  String supportedEntityType();

  /**
   * Process the offline entity push payload.
   *
   * @param offlineId device-generated UUID
   * @param deviceId the device identifier
   * @param offlineCreatedAt the device-local creation timestamp
   * @param payload the entity data as a typed map (deserialized from JSON)
   * @return SyncPushResponse indicating SYNCED or CONFLICT
   */
  SyncPushResponse handlePush(
      UUID offlineId, String deviceId, Instant offlineCreatedAt, Map<String, Object> payload);
}
