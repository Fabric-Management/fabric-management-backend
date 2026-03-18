package com.fabricmanagement.offline.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SyncConflictException}.
 *
 * <p>CR-11-09: Verifies error code generation, details map population, and HTTP status.
 */
class SyncConflictExceptionTest {

  @Test
  @DisplayName("should generate correct error code for Type 1 conflict")
  void type1ErrorCode() {
    SyncConflictException ex =
        new SyncConflictException(1, "abc-123", "Duplicate quote for same customer");

    assertThat(ex.getErrorCode()).isEqualTo("SYNC_CONFLICT_TYPE_1");
    assertThat(ex.getHttpStatus()).isEqualTo(409);
    assertThat(ex.getMessage()).isEqualTo("Duplicate quote for same customer");
  }

  @Test
  @DisplayName("should generate correct error code for Type 4 conflict")
  void type4ErrorCode() {
    SyncConflictException ex = new SyncConflictException(4, "xyz-789", "Duplicate customer taxId");

    assertThat(ex.getErrorCode()).isEqualTo("SYNC_CONFLICT_TYPE_4");
  }

  @Test
  @DisplayName("CR-11-03: should populate details map with syncConflictType and offlineId")
  void detailsMap() {
    SyncConflictException ex =
        new SyncConflictException(2, "offline-uuid-456", "PriceList changed");

    assertThat(ex.getDetails())
        .containsEntry("syncConflictType", 2)
        .containsEntry("offlineId", "offline-uuid-456");
  }

  @Test
  @DisplayName("should expose syncConflictType and offlineId via getters")
  void getters() {
    SyncConflictException ex = new SyncConflictException(3, "stock-conflict-id", "Lot sold out");

    assertThat(ex.getSyncConflictType()).isEqualTo(3);
    assertThat(ex.getOfflineId()).isEqualTo("stock-conflict-id");
  }
}
