package com.fabricmanagement.offline.dto;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mobile client sync pull response. Contains map of updated entities grouped by entityType. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncPullResponse {

  /** Server timestamp to be used by the mobile device as his next lastSyncTimestamp */
  private Instant serverTimestamp;

  /**
   * Delta data map. Key: Entity type (e.g. "TASK", "WAREHOUSE_LOCATION", "PRODUCT") Value: List of
   * entity DTOs that were modified or created since the last sync.
   */
  @Builder.Default private Map<String, List<?>> updates = new HashMap<>();
}
