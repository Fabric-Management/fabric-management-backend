package com.fabricmanagement.notification.hub.app.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.events.ProcessedEventRepository;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.notification.hub.app.TenantNotificationDispatcher;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.PermissionRecipientPort;
import com.fabricmanagement.notification.hub.domain.port.PlatformAdminRecipientPort;
import com.fabricmanagement.platform.auth.domain.event.SelfSignupCompletedEvent;
import com.fabricmanagement.product.fiber.domain.event.FiberRequestRejectedEvent;
import com.fabricmanagement.product.fiber.domain.event.FiberRequestSubmittedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformNotificationListener")
class PlatformNotificationListenerTest {

  private static final UUID TENANT = UUID.randomUUID();

  @Mock private TenantNotificationDispatcher dispatcher;
  @Mock private PlatformAdminRecipientPort platformAdmins;
  @Mock private PermissionRecipientPort permissionRecipients;
  @Mock private ProcessedEventRepository processedEvents;

  private PlatformNotificationListener listener;

  @BeforeEach
  void setUp() {
    listener =
        new PlatformNotificationListener(
            dispatcher,
            platformAdmins,
            permissionRecipients,
            new IdempotentEventHandler(processedEvents, new SimpleMeterRegistry()));
    when(processedEvents.tryInsert(any(), any())).thenReturn(1);
  }

  @SuppressWarnings("unchecked")
  private Supplier<Collection<UUID>> recipientsOf(String eventType, UUID tenant) {
    ArgumentCaptor<Supplier<Collection<UUID>>> recipients = ArgumentCaptor.forClass(Supplier.class);
    verify(dispatcher)
        .notifyInTenant(eq(tenant), recipients.capture(), eq(eventType), any(), any(), any());
    return recipients.getValue();
  }

  @Test
  @DisplayName("a fibre request goes to the platform administrators, in the platform tenant (B4)")
  void submittedGoesToPlatformAdministrators() {
    UUID admin = UUID.randomUUID();
    when(platformAdmins.findPlatformAdministrators()).thenReturn(List.of(admin));
    var event =
        new FiberRequestSubmittedEvent(
            TENANT, UUID.randomUUID(), UUID.randomUUID(), "CLY", "Lyocell", "Willow Cloth Ltd");

    listener.onFiberRequestSubmitted(event);

    ArgumentCaptor<Map<String, String>> payload = payloadCaptor();
    verify(dispatcher)
        .notifyInTenant(
            eq(TenantContext.SYSTEM_TENANT_ID),
            any(),
            eq(NotificationEventType.FIBER_REQUEST_SUBMITTED),
            payload.capture(),
            eq(event.getRequestId()),
            eq("FIBER_REQUEST"));
    assertThat(payload.getValue())
        .containsEntry("tenantName", "Willow Cloth Ltd")
        .containsEntry("fiberName", "Lyocell")
        .containsEntry("isoCode", "CLY");
    assertThat(
            recipientsOf(
                    NotificationEventType.FIBER_REQUEST_SUBMITTED, TenantContext.SYSTEM_TENANT_ID)
                .get())
        .containsExactly(admin);
  }

  @Test
  @DisplayName("a decision goes back to the requester and the tenant's catalogue managers")
  void rejectedGoesToRequesterAndCatalogueManagers() {
    UUID requester = UUID.randomUUID();
    UUID manager = UUID.randomUUID();
    when(permissionRecipients.findUsersWithPermission(TENANT, PermissionKey.FIBER_WRITE))
        .thenReturn(Set.of(manager, requester));
    var event =
        new FiberRequestRejectedEvent(
            TENANT, UUID.randomUUID(), requester, "CLY", "Lyocell", "Already in the catalogue");

    listener.onFiberRequestRejected(event);

    assertThat(recipientsOf(NotificationEventType.FIBER_REQUEST_REJECTED, TENANT).get())
        .containsExactlyInAnyOrder(requester, manager);
  }

  @Test
  @DisplayName("a new company is named with what it signed up for")
  void newCompanyCarriesItsPlan() {
    SelfSignupCompletedEvent playground = signup(false, "PLAYGROUND");
    SelfSignupCompletedEvent salesLed = signup(true, "TRIAL");
    SelfSignupCompletedEvent trial = signup(false, null);

    assertThat(PlatformNotificationListener.plan(playground)).isEqualTo("PLAYGROUND");
    assertThat(PlatformNotificationListener.plan(salesLed)).isEqualTo("SALES_LED");
    assertThat(PlatformNotificationListener.plan(trial)).isEqualTo("TRIAL");

    listener.onSelfSignupCompleted(playground);

    ArgumentCaptor<Map<String, String>> payload = payloadCaptor();
    verify(dispatcher)
        .notifyInTenant(
            eq(TenantContext.SYSTEM_TENANT_ID),
            any(),
            eq(NotificationEventType.NEW_TENANT_ONBOARDED),
            payload.capture(),
            eq(playground.getTrialTenantId()),
            eq("TENANT"));
    assertThat(payload.getValue())
        .containsEntry("tenantName", "Alder Mills Ltd")
        .containsEntry("plan", "PLAYGROUND");
  }

  private static SelfSignupCompletedEvent signup(boolean salesLed, String intent) {
    UUID tenant = UUID.randomUUID();
    return new SelfSignupCompletedEvent(
        tenant,
        "owner@alder.example",
        "Ada",
        "Mill",
        "Alder Mills Ltd",
        null,
        null,
        null,
        salesLed,
        List.of(),
        intent,
        tenant,
        "en");
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Map<String, String>> payloadCaptor() {
    return ArgumentCaptor.forClass(Map.class);
  }
}
