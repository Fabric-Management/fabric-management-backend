package com.fabricmanagement.common.infrastructure.config;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * i18n configuration — Spring {@link MessageSource} and {@link LocaleResolver} beans.
 *
 * <h2>Scope</h2>
 *
 * <p>This configuration is for <b>backend-only</b> text (email templates, scheduler notifications,
 * system audit messages). All UI-facing text is handled by the frontend's own i18n system.
 *
 * <h2>Cascade</h2>
 *
 * <ol>
 *   <li>User's preferred locale (from {@code User.preferredLocale})
 *   <li>Tenant default locale (from {@code TenantSettings.locale})
 *   <li>System fallback — English ({@code Locale.ENGLISH})
 * </ol>
 *
 * <h2>Files</h2>
 *
 * <ul>
 *   <li>{@code src/main/resources/i18n/messages_en.properties}
 *   <li>{@code src/main/resources/i18n/messages_tr.properties}
 * </ul>
 */
@Configuration
public class I18nConfig {

  /**
   * Spring {@link MessageSource} backed by {@code classpath:i18n/messages*.properties}.
   *
   * <p>UTF-8 encoding is mandatory for Turkish characters (ş, ğ, ü, ö, ç, İ).
   *
   * <p>{@code setFallbackToSystemLocale(false)} ensures we always use English when a translation
   * key is missing, instead of falling through to the JVM's system locale.
   */
  @Bean
  public MessageSource messageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasenames("i18n/messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false); // Always fall back to EN, not JVM locale
    source.setUseCodeAsDefaultMessage(true); // Return key code when message is missing (no crash)
    return source;
  }

  /**
   * Locale resolver that reads the {@code Accept-Language} HTTP header.
   *
   * <p>Supported locales: Turkish ({@code tr}) and English ({@code en}). Unknown locales resolve to
   * English. The actual locale population into thread-local context is handled by {@link
   * com.fabricmanagement.common.infrastructure.web.LocalizationFilter}.
   */
  @Bean
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.forLanguageTag("tr")));
    resolver.setDefaultLocale(Locale.ENGLISH);
    return resolver;
  }
}
