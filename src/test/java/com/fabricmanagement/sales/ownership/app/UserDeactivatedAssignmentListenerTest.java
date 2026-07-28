package com.fabricmanagement.sales.ownership.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.platform.user.domain.event.UserDeactivatedEvent;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDeactivatedAssignmentListenerTest {

  @Mock private IdempotentEventHandler idempotentEventHandler;
  @Mock private CustomerCommercialAssignmentService assignmentService;

  private UserDeactivatedAssignmentListener listener;
  private final Set<UUID> processedEventIds = new HashSet<>();

  @BeforeEach
  void setUp() {
    listener = new UserDeactivatedAssignmentListener(idempotentEventHandler, assignmentService);
    doAnswer(
            invocation -> {
              UUID eventId = invocation.getArgument(0);
              Runnable handler = invocation.getArgument(3);
              if (processedEventIds.add(eventId)) {
                handler.run();
              }
              return null;
            })
        .when(idempotentEventHandler)
        .executeOnce(any(UUID.class), any(Class.class), anyString(), any(Runnable.class));
  }

  @Test
  void closesAllAssignmentsAtEventOccurrenceAndIsIdempotent() {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-28T09:30:00Z");
    UserDeactivatedEvent event =
        new UserDeactivatedEvent(
            eventId,
            tenantId,
            "USER_DEACTIVATED",
            occurredAt,
            eventId.toString(),
            userId,
            "left company");

    listener.onUserDeactivated(event);
    listener.onUserDeactivated(event);

    verify(assignmentService).closeAllForDeactivatedRepresentative(tenantId, userId, occurredAt);
  }
}
