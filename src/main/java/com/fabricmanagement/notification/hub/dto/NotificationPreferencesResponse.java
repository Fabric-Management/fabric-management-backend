package com.fabricmanagement.notification.hub.dto;

import com.fabricmanagement.notification.hub.domain.NotificationEvent;
import com.fabricmanagement.notification.hub.domain.NotificationGroup;
import com.fabricmanagement.notification.hub.domain.NotificationImportance;
import java.util.List;

/**
 * What the preferences screen shows, ready to render: the groups, whether e-mail can be chosen in
 * each, and its current state for the signed-in user. The screen derives nothing from it.
 *
 * <p>In-app notifications are not a choice and are not listed: they are always delivered.
 */
public record NotificationPreferencesResponse(List<GroupPreference> groups) {

  /** {@code MIXED}: some of the group's notifications are e-mailed and some are not. */
  public enum EmailState {
    ON,
    OFF,
    MIXED
  }

  /**
   * @param emailAvailable at least one notification in the group can be switched; when false the
   *     group has nothing to choose and {@code emailState} is {@code ON}
   * @param criticalAlwaysEmailed the group has critical notifications, which are always e-mailed
   *     whatever the user chooses
   */
  public record GroupPreference(
      NotificationGroup group,
      boolean emailAvailable,
      EmailState emailState,
      boolean criticalAlwaysEmailed,
      List<EventPreference> events) {}

  /**
   * @param locked critical: always e-mailed, cannot be changed
   */
  public record EventPreference(
      NotificationEvent eventType,
      NotificationImportance importance,
      boolean emailEnabled,
      boolean locked) {}
}
