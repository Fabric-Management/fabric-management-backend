package com.fabricmanagement.notification.hub.app;

import com.fabricmanagement.notification.hub.domain.NotificationTemplate;
import com.fabricmanagement.notification.i18n.app.TranslationService;
import java.util.Map;
import java.util.UUID;

/** Shared rendering for queued and mandatory transactional delivery. */
public final class NotificationRenderer {
  private NotificationRenderer() {}

  public static Rendered render(
      TranslationService translations,
      UUID tenant,
      String locale,
      NotificationTemplate template,
      Map<String, String> payload) {
    return new Rendered(
        translations.translateAndRender(tenant, locale, template.getTitleKey(), payload),
        translations.translateAndRender(tenant, locale, template.getBodyKey(), payload));
  }

  public record Rendered(String title, String body) {}
}
