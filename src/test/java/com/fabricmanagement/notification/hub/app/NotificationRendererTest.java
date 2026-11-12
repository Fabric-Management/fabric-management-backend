package com.fabricmanagement.notification.hub.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.notification.hub.domain.NotificationTemplate;
import com.fabricmanagement.notification.i18n.app.TranslationService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NotificationRenderer")
class NotificationRendererTest {

  private static final UUID TENANT = UUID.randomUUID();

  private TranslationService translations;
  private NotificationTemplate template;

  @BeforeEach
  void setUp() {
    translations = mock(TranslationService.class);
    template = mock(NotificationTemplate.class);
    when(template.getTitleKey()).thenReturn("t");
    when(template.getBodyKey()).thenReturn("b");
    // A key with no translation comes back as the key itself, as TranslationService does.
    lenient()
        .when(translations.translate(any(), anyString(), anyString()))
        .thenAnswer(call -> call.getArgument(2));
  }

  private NotificationRenderer.Rendered render(
      String locale, String title, String body, Map<String, String> payload) {
    when(translations.translate(TENANT, locale, "t")).thenReturn(title);
    when(translations.translate(TENANT, locale, "b")).thenReturn(body);
    return NotificationRenderer.render(translations, TENANT, locale, template, payload);
  }

  @Test
  @DisplayName("a label turns the payload code into words")
  void labelTurnsCodeIntoWords() {
    when(translations.translate(TENANT, "TR", "notification.label.entityType.QUOTE"))
        .thenReturn("Teklif");

    var rendered =
        render(
            "TR",
            "{entityCode} onayınızı bekliyor",
            "{entityTypeLabel} {entityCode} onayınızı bekliyor.",
            Map.of("entityType", "QUOTE", "entityCode", "Q-123"));

    assertThat(rendered.title()).isEqualTo("Q-123 onayınızı bekliyor");
    assertThat(rendered.body()).isEqualTo("Teklif Q-123 onayınızı bekliyor.");
  }

  @Test
  @DisplayName("a hint is looked up under its own key")
  void hintHasItsOwnKey() {
    when(translations.translate(eq(TENANT), eq("EN"), eq("notification.hint.invoiceSide.PAYABLE")))
        .thenReturn("Pay it or contact the supplier.");

    var rendered = render("EN", "x", "Due. {invoiceSideHint}", Map.of("invoiceSide", "PAYABLE"));

    assertThat(rendered.body()).isEqualTo("Due. Pay it or contact the supplier.");
  }

  @Test
  @DisplayName("a code with no label is shown as it is rather than as a key")
  void missingLabelFallsBackToTheCode() {
    var rendered = render("EN", "x", "{entityTypeLabel} 7", Map.of("entityType", "NEW_KIND"));

    assertThat(rendered.body()).isEqualTo("NEW_KIND 7");
  }

  @Test
  @DisplayName("an optional part is dropped when its value is blank, kept when it is given")
  void optionalPart() {
    String body = "Q-1 reddedildi. [[Neden: {rejectionReason}. ]]Düzeltip yeniden gönderin.";

    assertThat(render("TR", "x", body, Map.of("rejectionReason", "")).body())
        .isEqualTo("Q-1 reddedildi. Düzeltip yeniden gönderin.");
    assertThat(render("TR", "x", body, Map.of("rejectionReason", "Fiyat yüksek")).body())
        .isEqualTo("Q-1 reddedildi. Neden: Fiyat yüksek. Düzeltip yeniden gönderin.");
  }

  @Test
  @DisplayName("money is formatted in the reader's language")
  void moneyFollowsTheLocale() {
    assertThat(
            render("TR", "x", "{amount} {currency}", Map.of("amount", "1250.5", "currency", "TRY"))
                .body())
        .isEqualTo("1.250,50 TRY");
    assertThat(
            render("EN", "x", "{amount} {currency}", Map.of("amount", "1250.5", "currency", "GBP"))
                .body())
        .isEqualTo("1,250.50 GBP");
  }

  @Test
  @DisplayName("an ISO date reads as a date in the reader's language")
  void dateFollowsTheLocale() {
    assertThat(render("TR", "x", "{thresholdDate}", Map.of("thresholdDate", "2026-12-05")).body())
        .isEqualTo("05.12.2026");
    assertThat(render("EN", "x", "{thresholdDate}", Map.of("thresholdDate", "2026-12-05")).body())
        .isEqualTo("5 Dec 2026");
  }

  @Test
  @DisplayName("hours read as days and hours")
  void durationReadsNaturally() {
    assertThat(render("TR", "x", "{ageDuration}", Map.of("ageHours", "30")).body())
        .isEqualTo("1 gün 6 saat");
    assertThat(render("EN", "x", "{ageDuration}", Map.of("ageHours", "49")).body())
        .isEqualTo("2 days 1 hour");
    assertThat(render("EN", "x", "{ageDuration}", Map.of("ageHours", "0")).body())
        .isEqualTo("less than an hour");
  }

  @Test
  @DisplayName("values are HTML-escaped and an unknown placeholder stays visible")
  void escapesAndKeepsUnknown() {
    var rendered = render("EN", "x", "{note} {missing}", Map.of("note", "<b>A & B</b>"));

    assertThat(rendered.body()).isEqualTo("&lt;b&gt;A &amp; B&lt;/b&gt; {missing}");
  }
}
