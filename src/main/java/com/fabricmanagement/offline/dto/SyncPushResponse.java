package com.fabricmanagement.offline.dto;

import com.fabricmanagement.offline.domain.SyncStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Response returned to the mobile client after a sync push attempt.
 *
 * <p>Contains the sync result: success (SYNCED) or conflict details (CONFLICT).
 */
@Data
@Builder
public class SyncPushResponse {

  private UUID offlineId;
  private UUID serverId;
  private SyncStatus status;
  private Integer conflictType;
  private String conflictReason;
  private Instant syncedAt;

  public static SyncPushResponse synced(UUID offlineId, UUID serverId, Instant syncedAt) {
    return SyncPushResponse.builder()
        .offlineId(offlineId)
        .serverId(serverId)
        .status(SyncStatus.SYNCED)
        .syncedAt(syncedAt)
        .build();
  }

  public static SyncPushResponse conflict(UUID offlineId, int conflictType, String conflictReason) {
    return SyncPushResponse.builder()
        .offlineId(offlineId)
        .status(SyncStatus.CONFLICT)
        .conflictType(conflictType)
        .conflictReason(conflictReason)
        .build();
  }
}
