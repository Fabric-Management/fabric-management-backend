package com.fabricmanagement.common.infrastructure.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.notification.i18n.domain.UserLocaleConfig;
import com.fabricmanagement.notification.i18n.infra.repository.TenantLocaleConfigRepository;
import com.fabricmanagement.notification.i18n.infra.repository.UserLocaleConfigRepository;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalizationService Unit Tests")
class LocalizationServiceTest {

  @Mock private MessageSource messageSource;
  @Mock private UserLocaleConfigRepository userLocaleConfigRepository;
  @Mock private TenantLocaleConfigRepository tenantLocaleConfigRepository;

  @InjectMocks private LocalizationService localizationService;

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  // ── Helpers ────────────────────────────────────────────────────────────────

  private UserLocaleConfig mockUserConfig(String locale, String timezone) {
    var cfg = UserLocaleConfig.create(TENANT_ID, USER_ID, locale);
    cfg.updateLocale(locale, null, timezone);
    return cfg;
  }

  // ── getMessage() ───────────────────────────────────────────────────────────

  @Nested
  @DisplayName("getMessage()")
  class GetMessageTests {

    @Test
    @DisplayName("mevcut key için mesajı döndürür")
    void should_return_message_for_existing_key() {
      when(messageSource.getMessage(
              "email.approval.approved.subject", null, Locale.forLanguageTag("tr")))
          .thenReturn("Talebiniz onaylandı");

      String result =
          localizationService.getMessage(
              "email.approval.approved.subject", null, Locale.forLanguageTag("tr"));

      assertThat(result).isEqualTo("Talebiniz onaylandı");
    }

    @Test
    @DisplayName("key bulunamadığında key kodunu döndürür (fail-safe)")
    void should_return_key_code_when_message_not_found() {
      when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
          .thenThrow(new NoSuchMessageException("missing.key"));

      String result = localizationService.getMessage("missing.key", null, Locale.ENGLISH);

      assertThat(result).isEqualTo("missing.key");
    }

    @Test
    @DisplayName("null locale EN olarak işlenir")
    void should_use_english_fallback_when_locale_is_null() {
      when(messageSource.getMessage("some.key", null, Locale.ENGLISH))
          .thenReturn("English message");

      String result = localizationService.getMessage("some.key", null, null);

      assertThat(result).isEqualTo("English message");
    }
  }

  // ── resolveLocaleForUser() ─────────────────────────────────────────────────

  @Nested
  @DisplayName("resolveLocaleForUser()")
  class ResolveLocaleTests {

    @Test
    @DisplayName("kullanıcı tercihi varsa onu döndürür")
    void should_return_user_locale_when_present() {
      when(userLocaleConfigRepository.findByUserId(USER_ID))
          .thenReturn(Optional.of(mockUserConfig("TR", null)));

      Locale result = localizationService.resolveLocaleForUser(TENANT_ID, USER_ID);

      assertThat(result.getLanguage()).isEqualTo("tr");
    }

    @Test
    @DisplayName("kullanıcı tercihi yoksa tenant default döner")
    void should_return_tenant_locale_when_user_has_no_config() {
      when(userLocaleConfigRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
      var tenantCfg = mock(com.fabricmanagement.notification.i18n.domain.TenantLocaleConfig.class);
      when(tenantCfg.getDefaultLocale()).thenReturn("TR");
      when(tenantLocaleConfigRepository.findByTenantId(TENANT_ID))
          .thenReturn(Optional.of(tenantCfg));

      Locale result = localizationService.resolveLocaleForUser(TENANT_ID, USER_ID);

      assertThat(result.getLanguage()).isEqualTo("tr");
    }

    @Test
    @DisplayName("hiçbir config yoksa EN fallback döner")
    void should_return_english_fallback_when_no_config() {
      when(userLocaleConfigRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
      when(tenantLocaleConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

      Locale result = localizationService.resolveLocaleForUser(TENANT_ID, USER_ID);

      assertThat(result).isEqualTo(Locale.ENGLISH);
    }
  }

  // ── resolveTimezoneForUser() ───────────────────────────────────────────────

  @Nested
  @DisplayName("resolveTimezoneForUser()")
  class ResolveTimezoneTests {

    @Test
    @DisplayName("kullanıcı timezone'u varsa onu döndürür")
    void should_return_user_timezone_when_present() {
      when(userLocaleConfigRepository.findByUserId(USER_ID))
          .thenReturn(Optional.of(mockUserConfig("TR", "Europe/Istanbul")));

      ZoneId result = localizationService.resolveTimezoneForUser(TENANT_ID, USER_ID);

      assertThat(result).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }

    @Test
    @DisplayName("timezone config yoksa UTC döner")
    void should_return_utc_fallback_when_no_config() {
      when(userLocaleConfigRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

      ZoneId result = localizationService.resolveTimezoneForUser(TENANT_ID, USER_ID);

      assertThat(result).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    @DisplayName("geçersiz timezone varsa UTC fallback döner")
    void should_return_utc_fallback_for_invalid_timezone() {
      when(userLocaleConfigRepository.findByUserId(USER_ID))
          .thenReturn(Optional.of(mockUserConfig("TR", "INVALID/ZONE")));

      ZoneId result = localizationService.resolveTimezoneForUser(TENANT_ID, USER_ID);

      assertThat(result).isEqualTo(ZoneId.of("UTC"));
    }
  }
}
