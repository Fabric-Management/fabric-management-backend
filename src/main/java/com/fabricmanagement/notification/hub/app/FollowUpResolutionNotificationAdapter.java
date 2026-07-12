package com.fabricmanagement.notification.hub.app;

import com.fabricmanagement.common.infrastructure.events.FollowUpResolutionNotifier;
import com.fabricmanagement.common.infrastructure.events.ResolvedFollowUp;
import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class FollowUpResolutionNotificationAdapter implements FollowUpResolutionNotifier {

  static final String EVENT_TYPE = "STUCK_FOLLOW_UP_RESOLVED";

  private final NotificationHubService notificationHub;
  private final DepartmentRecipientPort departmentRecipientPort;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void notifyResolved(ResolvedFollowUp event) {
    List<UUID> recipients = recipientsFor(event);
    if (recipients.isEmpty()) {
      log.warn(
          "No recipients for resolved follow-up notification: tenantId={} entityType={} referenceId={}",
          event.tenantId(),
          event.entityType(),
          event.referenceId());
      return;
    }

    Map<String, String> payload =
        Map.of(
            "entityRef",
            event.entityRef() == null ? "your recent request" : event.entityRef(),
            "summary",
            event.summary());
    notificationHub.notifyAll(
        recipients,
        event.tenantId(),
        EVENT_TYPE,
        payload,
        event.referenceId(),
        event.referenceType());
  }

  private List<UUID> recipientsFor(ResolvedFollowUp event) {
    if (event.affectedUserId() != null) {
      return List.of(event.affectedUserId());
    }

    String departmentKeyword = departmentKeyword(event.entityType());
    if (departmentKeyword == null) {
      return List.of();
    }
    return departmentRecipientPort.findManagersByDepartmentKeyword(
        event.tenantId(), departmentKeyword);
  }

  private String departmentKeyword(String entityType) {
    if ("SUPPLIER_QUOTE".equals(entityType)) {
      return "PROCUREMENT";
    }
    return null;
  }
}
