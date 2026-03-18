package com.fabricmanagement.offline.app;

import com.fabricmanagement.offline.domain.OfflineMetadata;
import com.fabricmanagement.offline.domain.SyncStatus;
import com.fabricmanagement.offline.dto.SyncPullRequest;
import com.fabricmanagement.offline.dto.SyncPullResponse;
import com.fabricmanagement.offline.dto.SyncPushResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates offline sync operations: push (mobile → backend) and pull (backend → mobile).
 *
 * <p>CR-11-11: This service is the central coordination point for all offline sync logic. It:
 *
 * <ul>
 *   <li>Validates incoming offline payloads
 *   <li>Detects conflicts (Type 1-4)
 *   <li>Creates/merges entities with appropriate sync status
 *   <li>Returns sync results to the mobile client
 * </ul>
 */
@Service
@Slf4j
public class SyncService {

  private final List<SyncDataProvider> dataProviders;
  private final Map<String, SyncPushHandler> pushHandlerMap;

  /**
   * Constructs SyncService with Spring-injected providers and handlers. Validates handler
   * uniqueness at startup to prevent silent conflicts.
   */
  public SyncService(List<SyncDataProvider> dataProviders, List<SyncPushHandler> pushHandlers) {
    this.dataProviders = dataProviders != null ? dataProviders : Collections.emptyList();

    // Build a handler map keyed by entityType for O(1) dispatch
    // Duplicate detection at startup prevents subtle runtime bugs
    if (pushHandlers != null && !pushHandlers.isEmpty()) {
      this.pushHandlerMap =
          pushHandlers.stream()
              .collect(
                  Collectors.toMap(
                      SyncPushHandler::supportedEntityType,
                      h -> h,
                      (h1, h2) -> {
                        throw new IllegalStateException(
                            "Duplicate SyncPushHandler for entityType '%s': %s and %s"
                                .formatted(
                                    h1.supportedEntityType(),
                                    h1.getClass().getSimpleName(),
                                    h2.getClass().getSimpleName()));
                      }));
    } else {
      this.pushHandlerMap = Collections.emptyMap();
    }

    log.info(
        "SyncService initialized: {} data providers, {} push handlers",
        this.dataProviders.size(),
        this.pushHandlerMap.size());
  }

  /**
   * Processes a single offline-created entity push from a mobile device.
   *
   * @param offlineId the device-generated UUID for the entity
   * @param deviceId the device identifier
   * @param offlineCreatedAt the device-local creation timestamp
   * @param entityType the type of entity being pushed
   * @param payload the entity data as a typed map
   * @return sync result (SYNCED or CONFLICT)
   * @throws IllegalArgumentException if entityType is not supported
   */
  public SyncPushResponse processPush(
      UUID offlineId,
      String deviceId,
      Instant offlineCreatedAt,
      String entityType,
      Map<String, Object> payload) {

    log.info(
        "Processing sync push: entityType={}, offlineId={}, deviceId={}",
        entityType,
        offlineId,
        deviceId);

    SyncPushHandler handler = pushHandlerMap.get(entityType.toUpperCase());
    if (handler != null) {
      return handler.handlePush(offlineId, deviceId, offlineCreatedAt, payload);
    }

    throw new IllegalArgumentException(
        "No sync push handler registered for entityType: "
            + entityType
            + ". Supported types: "
            + pushHandlerMap.keySet());
  }

  /** Processes a pull request from the mobile device to fetch delta data. */
  public SyncPullResponse processPull(UUID tenantId, SyncPullRequest request) {
    log.info(
        "Processing sync pull for tenant: {}, lastSync={}",
        tenantId,
        request.getLastSyncTimestamp());

    Map<String, List<?>> updates = new HashMap<>();
    Instant serverTime = Instant.now();
    int limit = request.getLimit() != null && request.getLimit() > 0 ? request.getLimit() : 500;

    List<String> requestedTypes =
        request.getEntityTypes() != null && !request.getEntityTypes().isBlank()
            ? Arrays.asList(request.getEntityTypes().split(","))
            : Collections.emptyList();

    for (SyncDataProvider provider : dataProviders) {
      String type = provider.entityType();
      if (requestedTypes.isEmpty() || requestedTypes.contains(type)) {
        try {
          List<?> data = provider.pullData(tenantId, request.getLastSyncTimestamp(), limit);
          if (data != null && !data.isEmpty()) {
            updates.put(type, data);
          }
        } catch (Exception e) {
          log.error("Failed to pull offline data for entityType: {}", type, e);
        }
      }
    }

    return SyncPullResponse.builder().serverTimestamp(serverTime).updates(updates).build();
  }

  /**
   * Marks an entity's offline metadata as synced. This is the common finalization step after
   * successful entity creation/merge.
   *
   * @param metadata the offline metadata to update
   * @param now the server timestamp
   */
  public void finalizeSyncSuccess(OfflineMetadata metadata, Instant now) {
    metadata.markSynced(now);
    log.debug("Sync finalized: offlineId={}, syncedAt={}", metadata.getOfflineId(), now);
  }

  /**
   * Marks an entity's offline metadata as conflicted.
   *
   * @param metadata the offline metadata to update
   * @param conflictType the conflict type (1-4)
   * @param reason detailed conflict description
   */
  public void markConflict(OfflineMetadata metadata, int conflictType, String reason) {
    metadata.markConflicted(reason);
    log.warn(
        "Sync conflict detected: offlineId={}, type={}, reason={}",
        metadata.getOfflineId(),
        conflictType,
        reason);
  }

  /**
   * Checks if a given offlineId has already been synced (idempotent sync support).
   *
   * @param syncStatus current sync status
   * @return true if the entity was already synced
   */
  public boolean isAlreadySynced(SyncStatus syncStatus) {
    return syncStatus == SyncStatus.SYNCED;
  }
}
