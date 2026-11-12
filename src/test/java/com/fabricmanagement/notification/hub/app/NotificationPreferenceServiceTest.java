package com.fabricmanagement.notification.hub.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.notification.hub.domain.NotificationChannel;
import com.fabricmanagement.notification.hub.domain.NotificationEvent;
import com.fabricmanagement.notification.hub.domain.NotificationGroup;
import com.fabricmanagement.notification.hub.domain.NotificationImportance;
import com.fabricmanagement.notification.hub.domain.NotificationTemplate;
import com.fabricmanagement.notification.hub.domain.UserNotificationPreference;
import com.fabricmanagement.notification.hub.dto.NotificationPreferencesResponse;
import com.fabricmanagement.notification.hub.dto.NotificationPreferencesResponse.EmailState;
import com.fabricmanagement.notification.hub.dto.NotificationPreferencesResponse.GroupPreference;
import com.fabricmanagement.notification.hub.infra.repository.NotificationTemplateRepository;
import com.fabricmanagement.notification.hub.infra.repository.UserNotificationPreferenceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationPreferenceService")
class NotificationPreferenceServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();

  @Mock private NotificationTemplateRepository templateRepo;
  @Mock private UserNotificationPreferenceRepository prefRepo;

  @InjectMocks private NotificationPreferenceService service;

  @Nested
  @DisplayName("catalogue()")
  class Catalogue {

    @Test
    @DisplayName("groups e-mail notifications; a user who never chose gets the template default")
    void usesTemplateDefaultsUntilTheUserChooses() {
      emailTemplates(
          template(NotificationEvent.APPROVAL_PENDING, NotificationImportance.HIGH, true),
          template(NotificationEvent.APPROVAL_REJECTED, NotificationImportance.HIGH, false));
      when(prefRepo.findAllByUserId(USER)).thenReturn(List.of());

      GroupPreference approvals = group(NotificationGroup.APPROVALS);

      assertThat(approvals.emailAvailable()).isTrue();
      assertThat(approvals.emailState()).isEqualTo(EmailState.MIXED);
      assertThat(approvals.events())
          .extracting(e -> e.eventType().name(), e -> e.emailEnabled())
          .containsExactly(
              org.assertj.core.groups.Tuple.tuple("APPROVAL_PENDING", true),
              org.assertj.core.groups.Tuple.tuple("APPROVAL_REJECTED", false));
    }

    @Test
    @DisplayName("the user's own choice wins over the default, both ways")
    void aUsersChoiceWins() {
      emailTemplates(
          template(NotificationEvent.APPROVAL_PENDING, NotificationImportance.HIGH, true),
          template(NotificationEvent.APPROVAL_REJECTED, NotificationImportance.HIGH, false));
      when(prefRepo.findAllByUserId(USER))
          .thenReturn(
              List.of(
                  choice("APPROVAL_PENDING", false), //
                  choice("APPROVAL_REJECTED", true)));

      GroupPreference approvals = group(NotificationGroup.APPROVALS);

      assertThat(approvals.events())
          .extracting(e -> e.eventType().name(), e -> e.emailEnabled())
          .containsExactly(
              org.assertj.core.groups.Tuple.tuple("APPROVAL_PENDING", false),
              org.assertj.core.groups.Tuple.tuple("APPROVAL_REJECTED", true));
    }

    @Test
    @DisplayName("group state is ON when all are on, OFF when none, MIXED otherwise")
    void groupStateFollowsItsNotifications() {
      emailTemplates(
          template(NotificationEvent.PO_DELIVERY_LATE, NotificationImportance.HIGH, true),
          template(NotificationEvent.RFQ_NO_RESPONSE, NotificationImportance.HIGH, true));
      when(prefRepo.findAllByUserId(USER)).thenReturn(List.of());
      assertThat(group(NotificationGroup.PROCUREMENT).emailState()).isEqualTo(EmailState.ON);

      when(prefRepo.findAllByUserId(USER))
          .thenReturn(List.of(choice("PO_DELIVERY_LATE", false), choice("RFQ_NO_RESPONSE", false)));
      assertThat(group(NotificationGroup.PROCUREMENT).emailState()).isEqualTo(EmailState.OFF);
    }

    @Test
    @DisplayName("critical notifications are locked and always on, whatever the user chose")
    void criticalIsLockedAndOn() {
      emailTemplates(
          template(NotificationEvent.BATCH_QC_FAILED, NotificationImportance.CRITICAL, false),
          template(NotificationEvent.BATCH_QC_PENDING, NotificationImportance.NORMAL, false));
      when(prefRepo.findAllByUserId(USER)).thenReturn(List.of(choice("BATCH_QC_FAILED", false)));

      GroupPreference quality = group(NotificationGroup.QUALITY);

      assertThat(quality.criticalAlwaysEmailed()).isTrue();
      assertThat(quality.emailAvailable()).isTrue();
      var critical =
          quality.events().stream()
              .filter(e -> e.eventType() == NotificationEvent.BATCH_QC_FAILED)
              .findFirst()
              .orElseThrow();
      assertThat(critical.locked()).isTrue();
      assertThat(critical.emailEnabled()).isTrue();
    }

    @Test
    @DisplayName("a group with only critical notifications has nothing to choose")
    void aCriticalOnlyGroupIsNotAvailable() {
      emailTemplates(
          template(NotificationEvent.ROUTING_FAILURE, NotificationImportance.CRITICAL, false));
      when(prefRepo.findAllByUserId(USER)).thenReturn(List.of());

      GroupPreference system = group(NotificationGroup.SYSTEM);

      assertThat(system.emailAvailable()).isFalse();
      assertThat(system.criticalAlwaysEmailed()).isTrue();
    }

    @Test
    @DisplayName("groups without an e-mail notification are not listed")
    void emptyGroupsAreOmitted() {
      emailTemplates(
          template(NotificationEvent.MIN_STOCK_ALERT, NotificationImportance.HIGH, false));
      when(prefRepo.findAllByUserId(USER)).thenReturn(List.of());

      NotificationPreferencesResponse response = service.catalogue(TENANT, USER);

      assertThat(response.groups())
          .extracting(GroupPreference::group)
          .containsExactly(NotificationGroup.INVENTORY);
    }

    private GroupPreference group(NotificationGroup group) {
      return service.catalogue(TENANT, USER).groups().stream()
          .filter(g -> g.group() == group)
          .findFirst()
          .orElseThrow();
    }
  }

  @Nested
  @DisplayName("setEvent()")
  class SetEvent {

    @Test
    @DisplayName("first choice writes a row; in-app stays on")
    void firstChoiceWritesARow() {
      emailTemplate(NotificationEvent.APPROVAL_PENDING, NotificationImportance.HIGH, true);
      when(prefRepo.findAllByUserId(USER)).thenReturn(List.of());

      service.setEvent(TENANT, USER, NotificationEvent.APPROVAL_PENDING, false);

      ArgumentCaptor<UserNotificationPreference> saved =
          ArgumentCaptor.forClass(UserNotificationPreference.class);
      verify(prefRepo).save(saved.capture());
      assertThat(saved.getValue().getEventType()).isEqualTo("APPROVAL_PENDING");
      assertThat(saved.getValue().isEmail()).isFalse();
      assertThat(saved.getValue().isInApp()).isTrue();
    }

    @Test
    @DisplayName("a later choice changes the existing row")
    void aLaterChoiceUpdatesTheRow() {
      emailTemplate(NotificationEvent.APPROVAL_PENDING, NotificationImportance.HIGH, true);
      UserNotificationPreference existing = choice("APPROVAL_PENDING", false);
      when(prefRepo.findAllByUserId(USER)).thenReturn(List.of(existing));

      service.setEvent(TENANT, USER, NotificationEvent.APPROVAL_PENDING, true);

      verify(prefRepo).save(existing);
      assertThat(existing.isEmail()).isTrue();
    }

    @Test
    @DisplayName("refuses a critical notification and writes nothing")
    void refusesCritical() {
      emailTemplate(NotificationEvent.BATCH_QC_FAILED, NotificationImportance.CRITICAL, false);

      assertThatThrownBy(
              () -> service.setEvent(TENANT, USER, NotificationEvent.BATCH_QC_FAILED, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("always");
      verify(prefRepo, never()).save(any());
    }

    @Test
    @DisplayName("refuses a notification that has no e-mail form")
    void refusesWithoutAnEmailTemplate() {
      when(templateRepo.findByTenantIdAndEventTypeAndChannelAndIsActiveTrue(
              TENANT, "AUTOMATION_ALERT", NotificationChannel.EMAIL))
          .thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> service.setEvent(TENANT, USER, NotificationEvent.AUTOMATION_ALERT, true))
          .isInstanceOf(IllegalArgumentException.class);
      verify(prefRepo, never()).save(any());
    }
  }

  @Nested
  @DisplayName("setGroup()")
  class SetGroup {

    @Test
    @DisplayName("writes every changeable notification of the group and leaves critical ones")
    void writesTheWholeGroupExceptCritical() {
      emailTemplates(
          template(NotificationEvent.BATCH_QC_FAILED, NotificationImportance.CRITICAL, false),
          template(NotificationEvent.BATCH_QC_PENDING, NotificationImportance.NORMAL, false),
          template(NotificationEvent.MIN_STOCK_ALERT, NotificationImportance.HIGH, false));
      when(prefRepo.findAllByUserId(USER)).thenReturn(List.of());

      service.setGroup(TENANT, USER, NotificationGroup.QUALITY, true);

      ArgumentCaptor<UserNotificationPreference> saved =
          ArgumentCaptor.forClass(UserNotificationPreference.class);
      verify(prefRepo, times(1)).save(saved.capture());
      assertThat(saved.getValue().getEventType()).isEqualTo("BATCH_QC_PENDING");
      assertThat(saved.getValue().isEmail()).isTrue();
    }

    @Test
    @DisplayName("refuses a group with nothing to change")
    void refusesAnEmptyGroup() {
      emailTemplates(
          template(NotificationEvent.ROUTING_FAILURE, NotificationImportance.CRITICAL, false));
      when(prefRepo.findAllByUserId(USER)).thenReturn(List.of());

      assertThatThrownBy(() -> service.setGroup(TENANT, USER, NotificationGroup.SYSTEM, false))
          .isInstanceOf(IllegalArgumentException.class);
      verify(prefRepo, never()).save(any());
    }
  }

  // ---- helpers ----

  private void emailTemplates(NotificationTemplate... templates) {
    when(templateRepo.findAllByTenantIdAndChannelAndIsActiveTrue(TENANT, NotificationChannel.EMAIL))
        .thenReturn(List.of(templates));
  }

  /**
   * Stubs the single-event lookup. The template mock is built first and passed in afterwards:
   * stubbing a mock inside {@code thenReturn(...)} of another stubbing is an unfinished stubbing.
   */
  private void emailTemplate(
      NotificationEvent event, NotificationImportance importance, boolean enabledByDefault) {
    NotificationTemplate template = template(event, importance, enabledByDefault);
    when(templateRepo.findByTenantIdAndEventTypeAndChannelAndIsActiveTrue(
            TENANT, event.name(), NotificationChannel.EMAIL))
        .thenReturn(Optional.of(template));
  }

  private static NotificationTemplate template(
      NotificationEvent event, NotificationImportance importance, boolean enabledByDefault) {
    var template = mock(NotificationTemplate.class);
    when(template.getEventType()).thenReturn(event.name());
    when(template.getImportance()).thenReturn(importance);
    when(template.getChannel()).thenReturn(NotificationChannel.EMAIL);
    when(template.isEnabledByDefault()).thenReturn(enabledByDefault);
    return template;
  }

  private static UserNotificationPreference choice(String eventType, boolean email) {
    var pref = UserNotificationPreference.createDefault(TENANT, USER, eventType);
    pref.update(true, email, true);
    return pref;
  }
}
