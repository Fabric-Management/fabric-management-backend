package com.fabricmanagement.notification.hub.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.notification.hub.domain.NotificationEvent;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The hub skips an event that has no template (a WARN, nothing delivered) and shows the raw key
 * when a template's wording is missing. Neither is visible to the person who added the event, so
 * this guard checks the tenants the wording comes from instead: the golden tenant, which every
 * customer tenant is provisioned from, and the platform's own tenant for the events that go to
 * platform administrators only (B4).
 *
 * <p>It also holds the wording to the rules agreed for NOTIF-UNIFY-1 S2: no codes, abbreviations or
 * English words in the Turkish text, the same placeholders in both languages, and a translation
 * behind every {@code {xLabel}} / {@code {xHint}} a template asks for.
 */
@DisplayName("Notification catalogue (golden and platform tenants)")
class NotificationCatalogueGuardIT extends AbstractIntegrationTest {

  private static final UUID GOLDEN = TenantContext.TEMPLATE_TENANT_ID;
  private static final UUID PLATFORM = TenantContext.SYSTEM_TENANT_ID;
  private static final List<UUID> SOURCES = List.of(GOLDEN, PLATFORM);

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");
  private static final Pattern DERIVED = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*?)(Label|Hint)}");

  /** A code (WORK_ORDER), an abbreviation or an English word has no place in Turkish wording. */
  private static final Pattern NOT_TURKISH =
      Pattern.compile(
          "\\b[A-Z]{2,}_[A-Z_]+\\b|\\b(KK|PO|RFQ|QC)\\b|deadline|KRİTİK|!", Pattern.UNICODE_CASE);

  /** English wording uses the same plain words: no codes or procurement abbreviations. */
  private static final Pattern NOT_PLAIN_ENGLISH =
      Pattern.compile("\\b[A-Z]{2,}_[A-Z_]+\\b|\\b(PO|RFQ|QC)\\b|CRITICAL|!");

  @Autowired private JdbcTemplate jdbc;

  private static UUID sourceOf(NotificationEvent event) {
    return event.platformOnly() ? PLATFORM : GOLDEN;
  }

  @Test
  @DisplayName(
      "every notification event has an in-app template where it is delivered, because in-app is"
          + " always delivered")
  void everyEventHasAnInAppTemplate() {
    List<String> missing =
        Arrays.stream(NotificationEvent.values())
            .filter(
                event ->
                    jdbc.queryForObject(
                            "SELECT count(*) FROM notification.notification_template"
                                + " WHERE tenant_id = ? AND event_type = ? AND channel = 'IN_APP'"
                                + " AND is_active = TRUE",
                            Long.class,
                            sourceOf(event),
                            event.name())
                        == 0)
            .map(Enum::name)
            .toList();

    assertThat(missing).as("events with no in-app template").isEmpty();
  }

  @Test
  @DisplayName("every template is for a known event, in the tenant that event is delivered from")
  void everyTemplateIsForAKnownEvent() {
    List<String> misplaced = new ArrayList<>();
    for (UUID tenant : SOURCES) {
      for (String type :
          jdbc.queryForList(
              "SELECT DISTINCT event_type FROM notification.notification_template"
                  + " WHERE tenant_id = ? AND is_active = TRUE",
              String.class,
              tenant)) {
        boolean known =
            NotificationEvent.find(type).map(event -> sourceOf(event).equals(tenant)).orElse(false);
        if (!known) {
          misplaced.add(type + " in " + tenant);
        }
      }
    }

    assertThat(misplaced)
        .as("templates for an unknown event, or in the wrong tenant for their audience")
        .isEmpty();
  }

  @Test
  @DisplayName("every template's title and body keys exist, so no raw key is ever shown")
  void everyTemplateKeyExists() {
    List<String> broken = new ArrayList<>();
    for (UUID tenant : SOURCES) {
      broken.addAll(
          jdbc.queryForList(
              """
              SELECT t.event_type || '/' || t.channel || ' -> ' || k.key_code
              FROM notification.notification_template t
              CROSS JOIN LATERAL (VALUES (t.title_key), (t.body_key)) AS k(key_code)
              WHERE t.tenant_id = ? AND t.is_active = TRUE
                AND NOT EXISTS (SELECT 1 FROM i18n.translation_key tk
                                WHERE tk.tenant_id = t.tenant_id AND tk.key_code = k.key_code)
              """,
              String.class,
              tenant));
    }

    assertThat(broken).as("template keys without a translation key").isEmpty();
  }

  @Test
  @DisplayName("every key the templates use has a Turkish value as well as the English default")
  void everyTemplateKeyHasTurkish() {
    List<String> withoutTurkish =
        templateTexts().stream().filter(text -> text.turkish() == null).map(Text::key).toList();

    assertThat(withoutTurkish).as("keys with no TR value").isEmpty();
  }

  @Test
  @DisplayName("Turkish wording has no codes, abbreviations, English words or exclamation marks")
  void turkishWordingIsPlain() {
    List<String> offending =
        templateTexts().stream()
            .filter(text -> text.turkish() != null && NOT_TURKISH.matcher(text.turkish()).find())
            .map(text -> text.key() + ": " + text.turkish())
            .toList();

    assertThat(offending).as("Turkish wording that breaks the S2 rules").isEmpty();
  }

  @Test
  @DisplayName("English wording has no codes, abbreviations or exclamation marks")
  void englishWordingIsPlain() {
    List<String> offending =
        templateTexts().stream()
            .filter(text -> NOT_PLAIN_ENGLISH.matcher(text.english()).find())
            .map(text -> text.key() + ": " + text.english())
            .toList();

    assertThat(offending).as("English wording that breaks the S2 rules").isEmpty();
  }

  @Test
  @DisplayName("both languages use the same placeholders, so neither shows a raw {name}")
  void bothLanguagesUseTheSamePlaceholders() {
    List<String> mismatched =
        templateTexts().stream()
            .filter(text -> text.turkish() != null)
            .filter(text -> !placeholders(text.english()).equals(placeholders(text.turkish())))
            .map(
                text ->
                    text.key()
                        + ": EN "
                        + placeholders(text.english())
                        + " / TR "
                        + placeholders(text.turkish()))
            .toList();

    assertThat(mismatched).as("keys whose EN and TR placeholders differ").isEmpty();
  }

  @Test
  @DisplayName("every {xLabel} / {xHint} a template asks for has translations in that tenant")
  void everyLabelHasTranslations() {
    List<String> missing = new ArrayList<>();
    for (Text text : templateTexts()) {
      Matcher derived = DERIVED.matcher(text.english());
      while (derived.find()) {
        String prefix =
            "notification." + derived.group(2).toLowerCase() + "." + derived.group(1) + ".";
        Long withTurkish =
            jdbc.queryForObject(
                """
                SELECT count(*) FROM i18n.translation_key tk
                JOIN i18n.translation_value tv ON tv.translation_key_id = tk.id
                WHERE tk.tenant_id = ? AND tk.key_code LIKE ? AND tv.locale = 'TR'
                """,
                Long.class,
                text.tenant(),
                prefix + "%");
        if (withTurkish == 0) {
          missing.add(text.key() + " -> " + prefix + "* in " + text.tenant());
        }
      }
    }

    assertThat(missing).as("labels a template uses but no tenant translation provides").isEmpty();
  }

  private record Text(UUID tenant, String key, String english, String turkish) {}

  /** The English default and Turkish value of every key a template uses. */
  private List<Text> templateTexts() {
    List<Text> texts = new ArrayList<>();
    for (UUID tenant : SOURCES) {
      for (Map<String, Object> row :
          jdbc.queryForList(
              """
              SELECT DISTINCT tk.key_code, tk.default_value,
                     (SELECT tv.value FROM i18n.translation_value tv
                       WHERE tv.translation_key_id = tk.id AND tv.locale = 'TR'
                         AND tv.is_active = TRUE LIMIT 1) AS turkish
              FROM notification.notification_template t
              CROSS JOIN LATERAL (VALUES (t.title_key), (t.body_key)) AS k(key_code)
              JOIN i18n.translation_key tk
                ON tk.tenant_id = t.tenant_id AND tk.key_code = k.key_code
              WHERE t.tenant_id = ? AND t.is_active = TRUE
              """,
              tenant)) {
        texts.add(
            new Text(
                tenant,
                (String) row.get("key_code"),
                (String) row.get("default_value"),
                (String) row.get("turkish")));
      }
    }
    return texts;
  }

  private static Set<String> placeholders(String text) {
    Set<String> names = new TreeSet<>();
    Matcher matcher = PLACEHOLDER.matcher(text == null ? "" : text);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }
}
