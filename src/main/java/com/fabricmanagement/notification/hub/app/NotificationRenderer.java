package com.fabricmanagement.notification.hub.app;

import com.fabricmanagement.notification.hub.domain.NotificationTemplate;
import com.fabricmanagement.notification.i18n.app.TranslationService;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared rendering for queued and mandatory transactional delivery.
 *
 * <p>Wording is written for the people who read it, so a template never shows a raw code. The
 * payload carries codes and figures; the template asks for what it needs:
 *
 * <ul>
 *   <li>{@code {name}}: the payload value as it is. {@code amount} and {@code currentTotal} are
 *       formatted as money in the reader's language (1.250,00 / 1,250.00); a {@code …Date} value in
 *       ISO form ({@code 2026-12-12}) as a date (12.12.2026 / 12 Dec 2026).
 *   <li>{@code {nameLabel}} / {@code {nameHint}}: the payload value of {@code name} translated
 *       through {@code notification.label.<name>.<VALUE>} / {@code
 *       notification.hint.<name>.<VALUE>} (for example {@code entityType = QUOTE} becomes "Teklif"
 *       / "Quote"). A value with no translation is shown as it is; {@code
 *       NotificationCatalogueGuardIT} keeps that from shipping.
 *   <li>{@code {nameDuration}}: the payload value of {@code nameHours} as a readable span ("1 gün 6
 *       saat" / "1 day 6 hours").
 *   <li>{@code [[ … ]]}: an optional part, dropped whole when any value inside it is missing or
 *       blank (for example "Neden: …" when no reason was given).
 * </ul>
 *
 * <p>The result is plain text. The bell shows it as text (the browser escapes it there), so it is
 * not HTML-escaped here; the e-mail channel escapes the body when it wraps it in HTML ({@code
 * EmailNotificationSender}).
 */
public final class NotificationRenderer {

  static final String LABEL_KEY = "notification.label.%s.%s";
  static final String HINT_KEY = "notification.hint.%s.%s";
  static final Set<String> MONEY_PARAMS = Set.of("amount", "currentTotal");

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");
  private static final Pattern OPTIONAL = Pattern.compile("\\[\\[(.*?)]]", Pattern.DOTALL);

  private NotificationRenderer() {}

  public static Rendered render(
      TranslationService translations,
      UUID tenant,
      String locale,
      NotificationTemplate template,
      Map<String, String> payload) {
    Map<String, String> values = payload == null ? Map.of() : payload;
    Resolver resolver = new Resolver(translations, tenant, locale, values);
    return new Rendered(
        fill(translations.translate(tenant, locale, template.getTitleKey()), resolver),
        fill(translations.translate(tenant, locale, template.getBodyKey()), resolver));
  }

  public record Rendered(String title, String body) {}

  static String fill(String text, Resolver resolver) {
    if (text == null) {
      return "";
    }
    Matcher optional = OPTIONAL.matcher(text);
    StringBuilder kept = new StringBuilder();
    while (optional.find()) {
      String part = optional.group(1);
      optional.appendReplacement(
          kept, Matcher.quoteReplacement(allPresent(part, resolver) ? part : ""));
    }
    optional.appendTail(kept);

    Matcher placeholder = PLACEHOLDER.matcher(kept);
    StringBuilder out = new StringBuilder();
    while (placeholder.find()) {
      String value = resolver.value(placeholder.group(1));
      placeholder.appendReplacement(
          out, Matcher.quoteReplacement(value == null ? placeholder.group() : value));
    }
    placeholder.appendTail(out);
    return out.toString().replaceAll("[ \\t]{2,}", " ").trim();
  }

  private static boolean allPresent(String part, Resolver resolver) {
    Matcher placeholder = PLACEHOLDER.matcher(part);
    while (placeholder.find()) {
      String value = resolver.value(placeholder.group(1));
      if (value == null || value.isBlank()) {
        return false;
      }
    }
    return true;
  }

  /** Values for one rendering: the payload, plus labels, hints, money and durations. */
  static final class Resolver {
    private final TranslationService translations;
    private final UUID tenant;
    private final String locale;
    private final Map<String, String> payload;

    Resolver(
        TranslationService translations, UUID tenant, String locale, Map<String, String> payload) {
      this.translations = translations;
      this.tenant = tenant;
      this.locale = locale;
      this.payload = payload;
    }

    String value(String name) {
      String direct = payload.get(name);
      if (direct != null) {
        if (MONEY_PARAMS.contains(name)) {
          return money(direct, locale);
        }
        return name.endsWith("Date") ? date(direct, locale) : direct;
      }
      if (name.endsWith("Label")) {
        return translated(LABEL_KEY, strip(name, "Label"));
      }
      if (name.endsWith("Hint")) {
        return translated(HINT_KEY, strip(name, "Hint"));
      }
      if (name.endsWith("Duration")) {
        String hours = payload.get(strip(name, "Duration") + "Hours");
        return hours == null ? null : duration(hours, locale);
      }
      return null;
    }

    private String translated(String keyPattern, String base) {
      String code = payload.get(base);
      if (code == null || code.isBlank()) {
        return null;
      }
      String key = keyPattern.formatted(base, code);
      String text = translations.translate(tenant, locale, key);
      return text == null || text.equals(key) ? code : text;
    }

    private static String strip(String name, String suffix) {
      return name.substring(0, name.length() - suffix.length());
    }
  }

  static String money(String raw, String locale) {
    try {
      NumberFormat format = NumberFormat.getNumberInstance(javaLocale(locale));
      format.setMinimumFractionDigits(2);
      format.setMaximumFractionDigits(2);
      return format.format(new BigDecimal(raw.trim()));
    } catch (NumberFormatException | ArithmeticException e) {
      return raw;
    }
  }

  static String date(String raw, String locale) {
    try {
      LocalDate day = LocalDate.parse(raw.trim());
      DateTimeFormatter format =
          switch (locale == null ? "" : locale.toUpperCase()) {
            case "TR" -> DateTimeFormatter.ofPattern("dd.MM.yyyy");
            case "EN", "" -> DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);
            default ->
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(javaLocale(locale));
          };
      return day.format(format);
    } catch (DateTimeParseException e) {
      return raw;
    }
  }

  static String duration(String rawHours, String locale) {
    long hours;
    try {
      hours = Math.max(0, Long.parseLong(rawHours.trim()));
    } catch (NumberFormatException e) {
      return rawHours;
    }
    boolean turkish = "TR".equalsIgnoreCase(locale);
    if (hours == 0) {
      return turkish ? "bir saatten az" : "less than an hour";
    }
    long days = hours / 24;
    long rest = hours % 24;
    StringBuilder span = new StringBuilder();
    if (days > 0) {
      span.append(days).append(turkish ? " gün" : days == 1 ? " day" : " days");
    }
    if (rest > 0) {
      if (!span.isEmpty()) {
        span.append(' ');
      }
      span.append(rest).append(turkish ? " saat" : rest == 1 ? " hour" : " hours");
    }
    return span.toString();
  }

  private static Locale javaLocale(String locale) {
    if (locale == null || locale.isBlank()) {
      return Locale.UK;
    }
    return "EN".equalsIgnoreCase(locale) ? Locale.UK : Locale.forLanguageTag(locale.toLowerCase());
  }
}
