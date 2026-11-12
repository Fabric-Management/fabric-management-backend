package com.fabricmanagement.notification.hub.app;

import com.fabricmanagement.notification.hub.domain.NotificationChannel;
import com.fabricmanagement.notification.hub.domain.NotificationEvent;
import com.fabricmanagement.notification.hub.domain.NotificationGroup;
import com.fabricmanagement.notification.hub.domain.NotificationImportance;
import com.fabricmanagement.notification.hub.domain.NotificationTemplate;
import com.fabricmanagement.notification.hub.domain.UserNotificationPreference;
import com.fabricmanagement.notification.hub.dto.NotificationPreferencesResponse;
import com.fabricmanagement.notification.hub.dto.NotificationPreferencesResponse.EmailState;
import com.fabricmanagement.notification.hub.dto.NotificationPreferencesResponse.EventPreference;
import com.fabricmanagement.notification.hub.dto.NotificationPreferencesResponse.GroupPreference;
import com.fabricmanagement.notification.hub.infra.repository.NotificationTemplateRepository;
import com.fabricmanagement.notification.hub.infra.repository.UserNotificationPreferenceRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a user may choose about notifications, and what they chose.
 *
 * <p>The only choice is whether a notification is <em>also</em> sent by e-mail. In-app
 * notifications are always delivered and critical ones always use every channel, so neither is
 * offered here, and the server refuses to change a critical one even if a client asks.
 *
 * <p>A user who never chose gets the template's default ({@code enabled_by_default}); the first
 * choice writes a row that wins from then on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPreferenceService {

  private final NotificationTemplateRepository templateRepo;
  private final UserNotificationPreferenceRepository prefRepo;

  /** The catalogue the preferences screen renders: groups, then their notifications. */
  @Transactional(readOnly = true)
  public NotificationPreferencesResponse catalogue(UUID tenantId, UUID userId) {
    Map<String, UserNotificationPreference> choices = choicesOf(userId);
    Map<NotificationGroup, List<EventPreference>> byGroup = new EnumMap<>(NotificationGroup.class);

    for (NotificationTemplate template : emailTemplates(tenantId)) {
      NotificationEvent event = NotificationEvent.find(template.getEventType()).orElse(null);
      if (event == null) {
        log.warn("E-mail template for unknown notification event {}", template.getEventType());
        continue;
      }
      boolean locked = isCritical(template);
      byGroup
          .computeIfAbsent(event.group(), g -> new ArrayList<>())
          .add(
              new EventPreference(
                  event,
                  template.getImportance(),
                  locked || emailOn(template, choices.get(template.getEventType())),
                  locked));
    }

    List<GroupPreference> groups = new ArrayList<>();
    for (NotificationGroup group : NotificationGroup.values()) {
      List<EventPreference> events = byGroup.get(group);
      if (events != null) {
        groups.add(groupOf(group, events));
      }
    }
    return new NotificationPreferencesResponse(groups);
  }

  /** Sets e-mail for one notification. Refuses a critical one and one without an e-mail form. */
  @Transactional
  public void setEvent(UUID tenantId, UUID userId, NotificationEvent event, boolean email) {
    NotificationTemplate template =
        templateRepo
            .findByTenantIdAndEventTypeAndChannelAndIsActiveTrue(
                tenantId, event.name(), NotificationChannel.EMAIL)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "E-mail is not available for notification " + event.name()));
    if (isCritical(template)) {
      throw new IllegalArgumentException(
          "Critical notification " + event.name() + " is always sent by e-mail");
    }
    write(tenantId, userId, event.name(), email, choicesOf(userId));
  }

  /**
   * Sets e-mail for every notification in a group that can be changed. Critical ones are left as
   * they are.
   */
  @Transactional
  public void setGroup(UUID tenantId, UUID userId, NotificationGroup group, boolean email) {
    Map<String, UserNotificationPreference> choices = choicesOf(userId);
    int changed = 0;
    for (NotificationTemplate template : emailTemplates(tenantId)) {
      NotificationEvent event = NotificationEvent.find(template.getEventType()).orElse(null);
      if (event == null || event.group() != group || isCritical(template)) {
        continue;
      }
      write(tenantId, userId, event.name(), email, choices);
      changed++;
    }
    if (changed == 0) {
      throw new IllegalArgumentException("Nothing in group " + group + " can be changed");
    }
  }

  // ---- helpers ----

  private GroupPreference groupOf(NotificationGroup group, List<EventPreference> events) {
    events.sort(Comparator.comparing(e -> e.eventType().name()));
    List<EventPreference> switchable = events.stream().filter(e -> !e.locked()).toList();
    boolean anyCritical = events.stream().anyMatch(EventPreference::locked);
    if (switchable.isEmpty()) {
      return new GroupPreference(group, false, EmailState.ON, anyCritical, List.copyOf(events));
    }
    long on = switchable.stream().filter(EventPreference::emailEnabled).count();
    EmailState state =
        on == switchable.size() ? EmailState.ON : on == 0 ? EmailState.OFF : EmailState.MIXED;
    return new GroupPreference(group, true, state, anyCritical, List.copyOf(events));
  }

  private void write(
      UUID tenantId,
      UUID userId,
      String eventType,
      boolean email,
      Map<String, UserNotificationPreference> choices) {
    UserNotificationPreference pref = choices.get(eventType);
    if (pref == null) {
      pref = UserNotificationPreference.createDefault(tenantId, userId, eventType);
      pref.update(true, email, true);
    } else {
      pref.update(true, email, pref.isPush());
    }
    prefRepo.save(pref);
  }

  private List<NotificationTemplate> emailTemplates(UUID tenantId) {
    return templateRepo.findAllByTenantIdAndChannelAndIsActiveTrue(
        tenantId, NotificationChannel.EMAIL);
  }

  private Map<String, UserNotificationPreference> choicesOf(UUID userId) {
    Map<String, UserNotificationPreference> choices = new HashMap<>();
    for (UserNotificationPreference pref : prefRepo.findAllByUserId(userId)) {
      choices.putIfAbsent(pref.getEventType(), pref);
    }
    return choices;
  }

  private static boolean emailOn(NotificationTemplate template, UserNotificationPreference pref) {
    return pref != null ? pref.isEmail() : template.isEnabledByDefault();
  }

  private static boolean isCritical(NotificationTemplate template) {
    return template.getImportance() == NotificationImportance.CRITICAL;
  }
}
