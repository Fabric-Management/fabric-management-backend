package com.fabricmanagement.offline.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fabricmanagement.offline.domain.OfflineMetadata;
import com.fabricmanagement.offline.domain.SyncStatus;
import com.fabricmanagement.offline.dto.SyncPullRequest;
import com.fabricmanagement.offline.dto.SyncPullResponse;
import com.fabricmanagement.offline.dto.SyncPushResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

  @Mock private SyncDataProvider dataProvider1;
  @Mock private SyncDataProvider dataProvider2;
  @Mock private SyncPushHandler pushHandler1;
  @Mock private SyncPushHandler pushHandler2;

  private SyncService syncService;

  @BeforeEach
  void setUp() {
    lenient().when(pushHandler1.supportedEntityType()).thenReturn("TASK");
    lenient().when(pushHandler2.supportedEntityType()).thenReturn("MATERIAL_MOVE");

    syncService =
        new SyncService(List.of(dataProvider1, dataProvider2), List.of(pushHandler1, pushHandler2));
  }

  @Test
  void constructor_DuplicatePushHandlers_ThrowsException() {
    // Arrange
    SyncPushHandler duplicateHandler = mock(SyncPushHandler.class);
    when(duplicateHandler.supportedEntityType()).thenReturn("TASK");

    // Act & Assert
    assertThatThrownBy(() -> new SyncService(List.of(), List.of(pushHandler1, duplicateHandler)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate SyncPushHandler for entityType 'TASK'");
  }

  @Test
  void processPush_RegisteredHandler_DelegatesToHandler() {
    // Arrange
    UUID offlineId = UUID.randomUUID();
    String deviceId = "test-device-1";
    Instant now = Instant.now();
    Map<String, Object> payload = Map.of("title", "Fix broken pipe");

    SyncPushResponse mockResponse = SyncPushResponse.synced(offlineId, UUID.randomUUID(), now);
    when(pushHandler1.handlePush(offlineId, deviceId, now, payload)).thenReturn(mockResponse);

    // Act
    SyncPushResponse response = syncService.processPush(offlineId, deviceId, now, "TASK", payload);

    // Assert
    assertThat(response.getStatus()).isEqualTo(SyncStatus.SYNCED);
    verify(pushHandler1).handlePush(offlineId, deviceId, now, payload);
    verify(pushHandler2, never()).handlePush(any(), any(), any(), any());
  }

  @Test
  void processPush_CaseInsensitiveEntityType_MatchesHandler() {
    // Arrange
    when(pushHandler1.handlePush(any(), any(), any(), any()))
        .thenReturn(SyncPushResponse.synced(UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

    // Act
    syncService.processPush(UUID.randomUUID(), "device", Instant.now(), "tAsK", Map.of());

    // Assert
    verify(pushHandler1).handlePush(any(), any(), any(), any());
  }

  @Test
  void processPush_UnregisteredHandler_ThrowsException() {
    // Act & Assert
    assertThatThrownBy(
            () ->
                syncService.processPush(
                    UUID.randomUUID(), "device", Instant.now(), "UNKNOWN_TYPE", Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No sync push handler registered for entityType: UNKNOWN_TYPE");
  }

  @Test
  void processPull_FetchesDataFromFilteredProviders() {
    // Arrange
    UUID tenantId = UUID.randomUUID();
    SyncPullRequest request = new SyncPullRequest();
    request.setEntityTypes("TASK,MATERIAL_MOVE"); // matches both providers
    request.setLimit(10);
    request.setLastSyncTimestamp(null);

    when(dataProvider1.entityType()).thenReturn("TASK");
    when(dataProvider2.entityType()).thenReturn("MATERIAL_MOVE");

    List<Object> mockTasks = List.of(new Object(), new Object());
    doReturn(mockTasks).when(dataProvider1).pullData(tenantId, null, 10);
    doReturn(List.of()).when(dataProvider2).pullData(tenantId, null, 10);

    // Act
    SyncPullResponse response = syncService.processPull(tenantId, request);

    // Assert
    assertThat(response.getUpdates()).containsEntry("TASK", mockTasks);
    assertThat(response.getUpdates())
        .doesNotContainKey("MATERIAL_MOVE"); // No data returned -> not in map
    verify(dataProvider1).pullData(tenantId, null, 10);
    verify(dataProvider2).pullData(tenantId, null, 10);
  }

  @Test
  void processPull_ProviderThrowsException_ContinuesAndReturnsSuccessfulData() {
    // Arrange
    UUID tenantId = UUID.randomUUID();
    SyncPullRequest request = new SyncPullRequest();
    request.setEntityTypes("TASK,MATERIAL_MOVE");

    when(dataProvider1.entityType()).thenReturn("TASK");
    when(dataProvider2.entityType()).thenReturn("MATERIAL_MOVE");

    when(dataProvider1.pullData(any(), any(), anyInt()))
        .thenThrow(new RuntimeException("DB Timeout"));
    List<Object> mockMoves = List.of(new Object());
    doReturn(mockMoves).when(dataProvider2).pullData(any(), any(), anyInt());

    // Act
    SyncPullResponse response = syncService.processPull(tenantId, request);

    // Assert
    assertThat(response.getUpdates()).doesNotContainKey("TASK");
    assertThat(response.getUpdates()).containsEntry("MATERIAL_MOVE", mockMoves);
  }

  @Test
  void processPull_NoEntityTypesFilter_CallsAllProviders() {
    // Arrange
    UUID tenantId = UUID.randomUUID();
    SyncPullRequest request = new SyncPullRequest();
    // Empty entityTypes

    when(dataProvider1.entityType()).thenReturn("TASK");
    when(dataProvider2.entityType()).thenReturn("MATERIAL_MOVE");

    // Act
    syncService.processPull(tenantId, request);

    // Assert
    verify(dataProvider1).pullData(eq(tenantId), any(), anyInt());
    verify(dataProvider2).pullData(eq(tenantId), any(), anyInt());
  }

  @Test
  void finalizeSyncSuccess_UpdatesMetadata() {
    // Arrange
    OfflineMetadata metadata = new OfflineMetadata();
    metadata.setOfflineId(UUID.randomUUID());
    Instant now = Instant.now();

    // Act
    syncService.finalizeSyncSuccess(metadata, now);

    // Assert
    assertThat(metadata.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
    assertThat(metadata.getSyncedAt()).isEqualTo(now);
    assertThat(metadata.getConflictReason()).isNull();
  }

  @Test
  void markConflict_UpdatesMetadata() {
    // Arrange
    OfflineMetadata metadata = new OfflineMetadata();
    metadata.setOfflineId(UUID.randomUUID());

    // Act
    syncService.markConflict(metadata, 1, "Duplicate entity found");

    // Assert
    assertThat(metadata.getSyncStatus()).isEqualTo(SyncStatus.CONFLICT);
    assertThat(metadata.getConflictReason()).isEqualTo("Duplicate entity found");
  }

  @Test
  void isAlreadySynced_ReturnsCorrectly() {
    assertThat(syncService.isAlreadySynced(SyncStatus.SYNCED)).isTrue();
    assertThat(syncService.isAlreadySynced(SyncStatus.PENDING)).isFalse();
    assertThat(syncService.isAlreadySynced(SyncStatus.CONFLICT)).isFalse();
  }
}
