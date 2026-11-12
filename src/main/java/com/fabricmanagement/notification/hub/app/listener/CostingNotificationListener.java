package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.costing.domain.event.CostVarianceDetectedEvent;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens for critical costing events (published via Modulith) and forwards them to
 * NotificationHub. Uses IdempotentEventHandler to ensure exactly-once notification delivery even if
 * the event is re-delivered after a crash (republish-outstanding-events-on-restart: true).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CostingNotificationListener {

  private final NotificationHubService notificationHubService;
  private final DepartmentRecipientPort departmentRecipientPort;
  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  public void onCostVarianceDetected(CostVarianceDetectedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onCostVarianceDetected",
        () -> {
          log.info("Handling CostVarianceDetectedEvent for entity {}", event.getEntityId());
          List<UUID> managers =
              departmentRecipientPort.findManagersByDepartmentKeyword(
                  event.getTenantId(), "COSTING", "PRODUCTION", "MANAGEMENT");

          if (managers.isEmpty()) return;

          notificationHubService.notifyAll(
              managers,
              event.getTenantId(),
              NotificationEventType.COST_VARIANCE_DETECTED,
              Map.of(
                  "entityType", event.getEntityType().name(),
                  "currentStage", event.getCurrentStage().name(),
                  "previousStage", event.getPreviousStage().name(),
                  "variancePercent", variancePercent(event.getVarianceRatio()),
                  "difference", event.getVarianceRatio().signum() >= 0 ? "HIGHER" : "LOWER",
                  "currentTotal", event.getCurrentTotal().toPlainString(),
                  "currency", event.getCurrency()),
              event.getEntityId(),
              event.getEntityType().name());
        });
  }

  /**
   * The signed ratio (0.153 = 15.3% over) as the whole percentage people read; the direction is
   * carried separately as {@code difference}. A comparison exists only when there is a previous
   * stage, which the costing module guarantees before it publishes the event.
   */
  static String variancePercent(java.math.BigDecimal ratio) {
    return ratio
        .abs()
        .movePointRight(2)
        .setScale(0, java.math.RoundingMode.HALF_UP)
        .toPlainString();
  }
}
