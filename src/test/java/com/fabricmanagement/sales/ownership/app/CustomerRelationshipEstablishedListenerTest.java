package com.fabricmanagement.sales.ownership.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.platform.tradingpartner.domain.event.CustomerRelationshipEstablishedEvent;
import com.fabricmanagement.platform.tradingpartner.domain.event.CustomerRelationshipSourceGate;
import com.fabricmanagement.sales.ownership.domain.OwnershipMode;
import com.fabricmanagement.sales.ownership.domain.OwnershipPolicy;
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
class CustomerRelationshipEstablishedListenerTest {

  @Mock private IdempotentEventHandler idempotentEventHandler;
  @Mock private OwnershipPolicyService ownershipPolicyService;
  @Mock private CustomerCommercialAssignmentService assignmentService;
  @Mock private OwnershipPolicy policy;

  private CustomerRelationshipEstablishedListener listener;
  private final Set<UUID> processedEventIds = new HashSet<>();

  @BeforeEach
  void setUp() {
    listener =
        new CustomerRelationshipEstablishedListener(
            idempotentEventHandler, ownershipPolicyService, assignmentService);
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
  void createsAcquisitionAssignmentOnceForNonExemptRelationship() {
    CustomerRelationshipEstablishedEvent event = event(UUID.randomUUID());
    when(ownershipPolicyService.requirePolicy(event.getTenantId())).thenReturn(policy);
    when(policy.getDefaultMode()).thenReturn(OwnershipMode.REQUIRED);

    listener.onCustomerRelationshipEstablished(event);
    listener.onCustomerRelationshipEstablished(event);

    verify(assignmentService)
        .createAcquisitionIfAbsent(
            event.getTenantId(),
            event.getCustomerId(),
            event.getAcquiredById(),
            event.getEstablishedAt());
  }

  @Test
  void nullAcquirerStillRepresentsAnEventButCreatesNoAssignment() {
    CustomerRelationshipEstablishedEvent event = event(null);
    when(ownershipPolicyService.requirePolicy(event.getTenantId())).thenReturn(policy);
    when(policy.getDefaultMode()).thenReturn(OwnershipMode.REQUIRED);

    listener.onCustomerRelationshipEstablished(event);

    verify(assignmentService, never()).createAcquisitionIfAbsent(any(), any(), any(), any());
  }

  @Test
  void exemptModeCreatesNoAssignment() {
    CustomerRelationshipEstablishedEvent event = event(UUID.randomUUID());
    when(ownershipPolicyService.requirePolicy(event.getTenantId())).thenReturn(policy);
    when(policy.getDefaultMode()).thenReturn(OwnershipMode.EXEMPT);

    listener.onCustomerRelationshipEstablished(event);

    verify(assignmentService, never()).createAcquisitionIfAbsent(any(), any(), any(), any());
  }

  private CustomerRelationshipEstablishedEvent event(UUID acquiredById) {
    return new CustomerRelationshipEstablishedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        acquiredById,
        Instant.parse("2026-07-28T09:00:00Z"),
        CustomerRelationshipSourceGate.CREATE);
  }
}
