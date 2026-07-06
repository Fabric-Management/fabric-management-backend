package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.notification.hub.app.NotificationContext;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.quote.domain.event.QuoteSendRequestRejectedEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuoteNotificationListener {

  private final NotificationHubService notificationHubService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onQuoteSendRequestRejected(QuoteSendRequestRejectedEvent event) {
    if (event.getRequesterId() == null || SystemUser.ID.equals(event.getRequesterId())) {
      log.debug(
          "QuoteSendRequestRejected: requester not a DB user (id={}) - skipping notification",
          event.getRequesterId());
      return;
    }

    notificationHubService.notify(
        NotificationContext.of(
            event.getTenantId(),
            event.getRequesterId(),
            NotificationEventType.APPROVAL_REJECTED,
            Map.of(
                "entityType", "QUOTE",
                "entityCode", nonNull(event.getQuoteNumber()),
                "rejectionReason", nonNull(event.getDecisionNote()),
                "referenceId", event.getQuoteId().toString(),
                "referenceType", "QUOTE"),
            event.getQuoteId(),
            "QUOTE"));
  }

  private static String nonNull(String value) {
    return value != null ? value : "";
  }
}
