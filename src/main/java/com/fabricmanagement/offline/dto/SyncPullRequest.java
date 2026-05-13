package com.fabricmanagement.offline.dto;

import java.time.Instant;
import lombok.Data;

/**
 * Request for pulling reference data changes since a given timestamp. Used by the mobile client on
 * reconnection to download only the changes that occurred while offline.
 */
@Data
public class SyncPullRequest {

  /**
   * The last successful sync timestamp from the mobile device. Only entities modified after this
   * timestamp will be returned.
   */
  private Instant lastSyncTimestamp;

  /**
   * Comma-separated list of entity types to pull (e.g. "TRADING_PARTNER,SALES_PRODUCT"). If null or
   * empty, all supported entity types are pulled.
   */
  private String entityTypes;

  /** Maximum number of records to return per entity type (pagination). Default: 500. */
  private Integer limit;
}
