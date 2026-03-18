package com.fabricmanagement.offline.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OfflineMetadata}.
 *
 * <p>CR-11-09: Covers factory methods, state transitions, and edge cases.
 */
class OfflineMetadataTest {

  private static final UUID OFFLINE_ID = UUID.randomUUID();
  private static final String DEVICE_ID = "device-iphone-12-001";
  private static final Instant DEVICE_TIME = Instant.parse("2026-03-18T08:00:00Z");
  private static final Instant SERVER_TIME = Instant.parse("2026-03-18T10:30:00Z");

  @Nested
  @DisplayName("pendingSync() factory method")
  class PendingSyncTests {

    @Test
    @DisplayName("should create metadata with PENDING status and all fields set")
    void shouldCreatePendingMetadata() {
      OfflineMetadata meta = OfflineMetadata.pendingSync(OFFLINE_ID, DEVICE_ID, DEVICE_TIME);

      assertThat(meta.getOfflineId()).isEqualTo(OFFLINE_ID);
      assertThat(meta.getDeviceId()).isEqualTo(DEVICE_ID);
      assertThat(meta.getOfflineCreatedAt()).isEqualTo(DEVICE_TIME);
      assertThat(meta.getSyncStatus()).isEqualTo(SyncStatus.PENDING);
      assertThat(meta.getSyncedAt()).isNull();
      assertThat(meta.getConflictReason()).isNull();
    }

    @Test
    @DisplayName("should accept null offlineId (for entities created online)")
    void shouldAcceptNullOfflineId() {
      OfflineMetadata meta = OfflineMetadata.pendingSync(null, DEVICE_ID, DEVICE_TIME);

      assertThat(meta.getOfflineId()).isNull();
      assertThat(meta.getSyncStatus()).isEqualTo(SyncStatus.PENDING);
    }
  }

  @Nested
  @DisplayName("markSynced()")
  class MarkSyncedTests {

    @Test
    @DisplayName("should transition PENDING → SYNCED with timestamp")
    void shouldTransitionToSynced() {
      OfflineMetadata meta = OfflineMetadata.pendingSync(OFFLINE_ID, DEVICE_ID, DEVICE_TIME);

      meta.markSynced(SERVER_TIME);

      assertThat(meta.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
      assertThat(meta.getSyncedAt()).isEqualTo(SERVER_TIME);
      assertThat(meta.getConflictReason()).isNull();
    }

    @Test
    @DisplayName("should clear conflictReason when syncing after resolution")
    void shouldClearConflictReasonOnResolvedToSynced() {
      OfflineMetadata meta = OfflineMetadata.pendingSync(OFFLINE_ID, DEVICE_ID, DEVICE_TIME);
      meta.markConflicted("TİP 1: Aynı müşteriye duplicate Quote");
      meta.markResolved();

      meta.markSynced(SERVER_TIME);

      assertThat(meta.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
      assertThat(meta.getConflictReason()).isNull();
    }

    @Test
    @DisplayName("should throw on illegal SYNCED → SYNCED transition")
    void shouldRejectSyncedToSynced() {
      OfflineMetadata meta = OfflineMetadata.pendingSync(OFFLINE_ID, DEVICE_ID, DEVICE_TIME);
      meta.markSynced(SERVER_TIME);

      assertThatThrownBy(() -> meta.markSynced(SERVER_TIME))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("SYNCED → SYNCED");
    }

    @Test
    @DisplayName("should throw on illegal CONFLICT → SYNCED transition")
    void shouldRejectConflictToSynced() {
      OfflineMetadata meta = OfflineMetadata.pendingSync(OFFLINE_ID, DEVICE_ID, DEVICE_TIME);
      meta.markConflicted("reason");

      assertThatThrownBy(() -> meta.markSynced(SERVER_TIME))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("CONFLICT → SYNCED");
    }
  }

  @Nested
  @DisplayName("markConflicted()")
  class MarkConflictedTests {

    @Test
    @DisplayName("should transition PENDING → CONFLICT with reason")
    void shouldTransitionToConflict() {
      OfflineMetadata meta = OfflineMetadata.pendingSync(OFFLINE_ID, DEVICE_ID, DEVICE_TIME);

      meta.markConflicted("TİP 2: PriceList offline'dan sonra güncellendi");

      assertThat(meta.getSyncStatus()).isEqualTo(SyncStatus.CONFLICT);
      assertThat(meta.getConflictReason())
          .isEqualTo("TİP 2: PriceList offline'dan sonra güncellendi");
    }

    @Test
    @DisplayName("should throw on illegal SYNCED → CONFLICT transition")
    void shouldRejectSyncedToConflict() {
      OfflineMetadata meta = OfflineMetadata.pendingSync(OFFLINE_ID, DEVICE_ID, DEVICE_TIME);
      meta.markSynced(SERVER_TIME);

      assertThatThrownBy(() -> meta.markConflicted("reason"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("SYNCED → CONFLICT");
    }
  }

  @Nested
  @DisplayName("markResolved()")
  class MarkResolvedTests {

    @Test
    @DisplayName("should transition CONFLICT → RESOLVED")
    void shouldTransitionToResolved() {
      OfflineMetadata meta = OfflineMetadata.pendingSync(OFFLINE_ID, DEVICE_ID, DEVICE_TIME);
      meta.markConflicted("TİP 3: Stok tükendi");

      meta.markResolved();

      assertThat(meta.getSyncStatus()).isEqualTo(SyncStatus.RESOLVED);
    }

    @Test
    @DisplayName("should throw on illegal PENDING → RESOLVED transition")
    void shouldRejectPendingToResolved() {
      OfflineMetadata meta = OfflineMetadata.pendingSync(OFFLINE_ID, DEVICE_ID, DEVICE_TIME);

      assertThatThrownBy(meta::markResolved)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("PENDING → RESOLVED");
    }
  }

  @Nested
  @DisplayName("null syncStatus (first-time set)")
  class NullStatusTests {

    @Test
    @DisplayName("should allow markSynced on null status (first time)")
    void shouldAllowSyncOnNullStatus() {
      OfflineMetadata meta = new OfflineMetadata();
      assertThat(meta.getSyncStatus()).isNull();

      meta.markSynced(SERVER_TIME);

      assertThat(meta.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
    }
  }
}
