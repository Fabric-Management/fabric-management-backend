package com.fabricmanagement.notification.hub.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.ResolvedFollowUp;
import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class FollowUpResolutionNotificationAdapterTest {

  @Mock private NotificationHubService notificationHub;
  @Mock private DepartmentRecipientPort departmentRecipientPort;

  private FollowUpResolutionNotificationAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new FollowUpResolutionNotificationAdapter(notificationHub, departmentRecipientPort);
  }

  @Test
  void notifiesAffectedUserWithReferenceAndPayload() {
    UUID tenantId = UUID.randomUUID();
    UUID affectedUserId = UUID.randomUUID();
    UUID referenceId = UUID.randomUUID();
    ResolvedFollowUp event =
        event(tenantId, affectedUserId, "SUPPLIER_QUOTE", "SQ-1042", referenceId);

    adapter.notifyResolved(event);

    ArgumentCaptor<Map<String, String>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationHub)
        .notifyAll(
            eq(List.of(affectedUserId)),
            eq(tenantId),
            eq(FollowUpResolutionNotificationAdapter.EVENT_TYPE),
            payloadCaptor.capture(),
            eq(referenceId),
            eq("SUPPLIER_QUOTE"));
    assertThat(payloadCaptor.getValue()).containsEntry("entityRef", "SQ-1042");
    verifyNoInteractions(departmentRecipientPort);
  }

  @Test
  void resolvesProcurementManagersWhenAffectedUserIsUnknown() {
    UUID tenantId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    UUID referenceId = UUID.randomUUID();
    when(departmentRecipientPort.findManagersByDepartmentKeyword(tenantId, "PROCUREMENT"))
        .thenReturn(List.of(managerId));

    adapter.notifyResolved(event(tenantId, null, "SUPPLIER_QUOTE", "SQ-1042", referenceId));

    verify(notificationHub)
        .notifyAll(
            eq(List.of(managerId)),
            eq(tenantId),
            eq(FollowUpResolutionNotificationAdapter.EVENT_TYPE),
            anyMap(),
            eq(referenceId),
            eq("SUPPLIER_QUOTE"));
  }

  @Test
  void emptyRecipientsAreLoggedAndSkipped(CapturedOutput output) {
    UUID tenantId = UUID.randomUUID();
    when(departmentRecipientPort.findManagersByDepartmentKeyword(tenantId, "PROCUREMENT"))
        .thenReturn(List.of());

    adapter.notifyResolved(event(tenantId, null, "SUPPLIER_QUOTE", "SQ-1042", UUID.randomUUID()));

    verifyNoInteractions(notificationHub);
    assertThat(output.getAll()).contains("No recipients for resolved follow-up notification");
  }

  @Test
  void notificationRunsInRequiresNewTransaction() throws NoSuchMethodException {
    Method method =
        FollowUpResolutionNotificationAdapter.class.getMethod(
            "notifyResolved", ResolvedFollowUp.class);

    Transactional transactional = method.getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
  }

  private ResolvedFollowUp event(
      UUID tenantId, UUID affectedUserId, String entityType, String entityRef, UUID referenceId) {
    return new ResolvedFollowUp(
        tenantId,
        affectedUserId,
        entityType,
        entityRef,
        referenceId,
        "SUPPLIER_QUOTE",
        "Purchase order creation is resolved.");
  }
}
