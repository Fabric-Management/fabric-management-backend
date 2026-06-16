package com.fabricmanagement.notification.hub.app.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.events.ProcessedEventRepository;
import com.fabricmanagement.costing.domain.calculation.CostEntityType;
import com.fabricmanagement.costing.domain.calculation.CostStage;
import com.fabricmanagement.costing.domain.event.CostVarianceDetectedEvent;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CostingNotificationListenerTest {

  @Mock private NotificationHubService notificationHubService;
  @Mock private DepartmentRecipientPort departmentRecipientPort;
  @Mock private ProcessedEventRepository processedEventRepository;

  private IdempotentEventHandler idempotentHandler;
  private CostingNotificationListener listener;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    idempotentHandler =
        new IdempotentEventHandler(processedEventRepository, new SimpleMeterRegistry());
    listener =
        new CostingNotificationListener(
            notificationHubService, departmentRecipientPort, idempotentHandler);
  }

  @Test
  void onCostVarianceDetected_whenNotProcessed_sendsNotification() {
    CostVarianceDetectedEvent event =
        new CostVarianceDetectedEvent(
            tenantId,
            UUID.randomUUID(),
            CostEntityType.WORK_ORDER,
            UUID.randomUUID(),
            CostStage.ACTUAL,
            CostStage.PLANNED,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150),
            BigDecimal.valueOf(0.50),
            "USD");

    when(processedEventRepository.tryInsert(eq(event.getEventId()), any())).thenReturn(1);
    when(departmentRecipientPort.findManagersByDepartmentKeyword(
            tenantId, "COSTING", "PRODUCTION", "MANAGEMENT"))
        .thenReturn(List.of(UUID.randomUUID()));

    listener.onCostVarianceDetected(event);

    verify(notificationHubService, times(1))
        .notifyAll(
            any(),
            eq(tenantId),
            eq(NotificationEventType.COST_VARIANCE_DETECTED),
            any(),
            eq(event.getEntityId()),
            eq("WORK_ORDER"));
  }

  @Test
  void onCostVarianceDetected_whenRedelivered_dedupesAndIgnores() {
    CostVarianceDetectedEvent event =
        new CostVarianceDetectedEvent(
            tenantId,
            UUID.randomUUID(),
            CostEntityType.WORK_ORDER,
            UUID.randomUUID(),
            CostStage.ACTUAL,
            CostStage.PLANNED,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150),
            BigDecimal.valueOf(0.50),
            "USD");

    // Simulate first delivery success, second delivery fail
    when(processedEventRepository.tryInsert(eq(event.getEventId()), any()))
        .thenReturn(1)
        .thenReturn(0);
    when(departmentRecipientPort.findManagersByDepartmentKeyword(
            tenantId, "COSTING", "PRODUCTION", "MANAGEMENT"))
        .thenReturn(List.of(UUID.randomUUID()));

    // Delivery 1
    listener.onCostVarianceDetected(event);
    // Delivery 2 (Redelivery)
    listener.onCostVarianceDetected(event);

    // Assert it was only sent ONCE
    verify(notificationHubService, times(1)).notifyAll(any(), any(), any(), any(), any(), any());
  }
}
