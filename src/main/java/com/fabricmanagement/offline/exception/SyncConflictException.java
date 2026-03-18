package com.fabricmanagement.offline.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * Thrown when an offline-created entity attempts to synchronize but a business-logic conflict
 * occurs (e.g. PriceList updated, Quote ID clash, etc.) which matches one of the 4 Phase-11
 * Configured Conflict Types.
 *
 * <p>Phase 11 Offline Sync Conflict Types:
 *
 * <ul>
 *   <li>TYPE 1: Same customer clash (e.g. Sales reps add the same custom quote at a fair/show).
 *   <li>TYPE 2: Price modification conflict (PriceList updated online before offline entity was
 *       synced).
 *   <li>TYPE 3: Stock conflict (Requested quantity/lot was sold/reserved online meanwhile).
 *   <li>TYPE 4: Duplicate Customer creation (taxId or email match).
 * </ul>
 *
 * <p><b>CR-11-03 fix:</b> Constructor populates {@code details} map with {@code syncConflictType}
 * and {@code offlineId} so that {@code GlobalExceptionHandler} serializes them into the API
 * response. The mobile client can read these fields to render the appropriate Conflict Resolution
 * UI.
 */
public class SyncConflictException extends DomainException {

  private final int syncConflictType;
  private final String offlineId;

  public SyncConflictException(int conflictType, String offlineId, String message) {
    super(message, "SYNC_CONFLICT_TYPE_" + conflictType, 409);
    this.syncConflictType = conflictType;
    this.offlineId = offlineId;
    // CR-11-03: populate details so GlobalExceptionHandler serializes them
    this.withDetail("syncConflictType", conflictType);
    this.withDetail("offlineId", offlineId);
  }

  public int getSyncConflictType() {
    return syncConflictType;
  }

  public String getOfflineId() {
    return offlineId;
  }
}
