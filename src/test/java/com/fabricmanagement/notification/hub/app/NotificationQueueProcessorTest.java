package com.fabricmanagement.notification.hub.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.notification.hub.app.NotificationQueueScanner.QueueRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The worker runs on a scheduler thread with no tenant, over tables that enforce row-level
 * security. It must find due rows without a tenant, and process each one inside that row's own
 * tenant — the e-mail outbox read an empty queue for exactly this reason.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // setUp stubs the empty stuck scan
@DisplayName("NotificationQueueProcessor")
class NotificationQueueProcessorTest {

  private static final UUID TENANT_A = UUID.randomUUID();
  private static final UUID TENANT_B = UUID.randomUUID();

  @Mock private NotificationQueueScanner scanner;
  @Mock private NotificationItemProcessor itemProcessor;

  private NotificationQueueProcessor processor;

  @BeforeEach
  void setUp() {
    TenantContext.clear();
    processor = new NotificationQueueProcessor(scanner, itemProcessor);
    ReflectionTestUtils.setField(processor, "batchSize", 50);
    ReflectionTestUtils.setField(processor, "stuckThresholdMinutes", 5);
    when(scanner.stuck(any())).thenReturn(List.of());
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("processes each row inside its own tenant and leaves no tenant behind")
  void processesEachRowInsideItsOwnTenant() {
    QueueRef a = new QueueRef(UUID.randomUUID(), TENANT_A, "APPROVAL_PENDING");
    QueueRef b = new QueueRef(UUID.randomUUID(), TENANT_B, "MIN_STOCK_ALERT");
    when(scanner.pending(50)).thenReturn(List.of(a, b));
    List<UUID> tenantsSeen = new ArrayList<>();
    doAnswer(
            call -> {
              tenantsSeen.add(TenantContext.getCurrentTenantIdOrNull());
              return null;
            })
        .when(itemProcessor)
        .processItem(any());

    processor.processQueue();

    assertThat(tenantsSeen).containsExactly(TENANT_A, TENANT_B);
    verify(itemProcessor).processItem(a.id());
    verify(itemProcessor).processItem(b.id());
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
  }

  @Test
  @DisplayName("one failing row does not stop the rows after it")
  void aFailingRowDoesNotStopTheRest() {
    QueueRef broken = new QueueRef(UUID.randomUUID(), TENANT_A, "APPROVAL_PENDING");
    QueueRef fine = new QueueRef(UUID.randomUUID(), TENANT_B, "MIN_STOCK_ALERT");
    when(scanner.pending(50)).thenReturn(List.of(broken, fine));
    doThrow(new IllegalStateException("db down")).when(itemProcessor).processItem(broken.id());

    processor.processQueue();

    verify(itemProcessor).processItem(fine.id());
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
  }

  @Test
  @DisplayName("an empty queue does nothing")
  void anEmptyQueueDoesNothing() {
    when(scanner.pending(50)).thenReturn(List.of());

    processor.processQueue();

    verify(itemProcessor, never()).processItem(any());
  }

  @Test
  @DisplayName("a failing scan is logged and does not escape the scheduler")
  void aFailingScanDoesNotEscape() {
    when(scanner.pending(50)).thenThrow(new IllegalStateException("system datasource down"));

    processor.processQueue();

    verify(itemProcessor, never()).processItem(any());
  }

  @Test
  @DisplayName("recovers stuck rows inside their own tenant")
  void recoversStuckRowsInsideTheirTenant() {
    QueueRef stuck = new QueueRef(UUID.randomUUID(), TENANT_A, "APPROVAL_PENDING");
    when(scanner.stuck(any())).thenReturn(List.of(stuck));
    when(scanner.pending(50)).thenReturn(List.of());
    List<UUID> tenantsSeen = new ArrayList<>();
    doAnswer(
            call -> {
              tenantsSeen.add(TenantContext.getCurrentTenantIdOrNull());
              return null;
            })
        .when(itemProcessor)
        .recoverStuckProcessingItem(any(), any(Instant.class));

    processor.processQueue();

    assertThat(tenantsSeen).containsExactly(TENANT_A);
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
  }
}
