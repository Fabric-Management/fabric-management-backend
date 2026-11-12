package com.fabricmanagement.notification.hub.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NotificationEvent vocabulary")
class NotificationEventTest {

  @Test
  @DisplayName("the string constants the listeners use and the published enum are the same set")
  void constantsAndEnumAgree() throws IllegalAccessException {
    Set<String> constants = new TreeSet<>();
    for (Field field : NotificationEventType.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
        constants.add((String) field.get(null));
      }
    }
    Set<String> enumNames = new TreeSet<>();
    Arrays.stream(NotificationEvent.values()).forEach(e -> enumNames.add(e.name()));

    assertThat(constants).as("NotificationEventType constants").isEqualTo(enumNames);
  }

  @Test
  @DisplayName("every event belongs to a group")
  void everyEventHasAGroup() {
    for (NotificationEvent event : NotificationEvent.values()) {
      assertThat(event.group()).as(event.name()).isNotNull();
    }
  }

  @Test
  @DisplayName("looks an event up by its stored string, and knows when it is not one")
  void findsByStoredString() {
    assertThat(NotificationEvent.find("APPROVAL_PENDING"))
        .contains(NotificationEvent.APPROVAL_PENDING);
    assertThat(NotificationEvent.find("NOT_AN_EVENT")).isEmpty();
    assertThat(NotificationEvent.find(null)).isEmpty();
  }
}
