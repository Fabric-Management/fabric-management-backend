package com.fabricmanagement.notification.i18n.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.notification.i18n.domain.TranslationKey;
import com.fabricmanagement.notification.i18n.domain.TranslationValue;
import com.fabricmanagement.notification.i18n.infra.repository.TenantLocaleConfigRepository;
import com.fabricmanagement.notification.i18n.infra.repository.TranslationKeyRepository;
import com.fabricmanagement.notification.i18n.infra.repository.TranslationValueRepository;
import com.fabricmanagement.notification.i18n.infra.repository.UserLocaleConfigRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TranslationService Unit Tests")
class TranslationServiceTest {

  @Mock private TranslationKeyRepository translationKeyRepo;
  @Mock private TranslationValueRepository translationValueRepo;
  @Mock private TenantLocaleConfigRepository tenantLocaleConfigRepo;
  @Mock private UserLocaleConfigRepository userLocaleConfigRepo;

  @InjectMocks private TranslationService translationService;

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final String KEY_CODE = "notification.work_order_pending_approval.title";

  // TranslationKey fabrikası — NoArgsConstructor protected olduğu için spy kullanamayız,
  // TranslationKey.of() factory'sini kullanırız (tenantId set edilmez ama test için sorun değil)
  private TranslationKey makeKey(String keyCode, String defaultValue) {
    return TranslationKey.of(keyCode, "NOTIFICATION", defaultValue, null);
  }

  private TranslationValue makeValue(String value) {
    // TranslationKey ve locale persist edilmese de getValue() çalışır
    return TranslationValue.of(makeKey(KEY_CODE, value), "TR", value, false);
  }

  @Nested
  @DisplayName("translate()")
  class TranslateTests {

    @Test
    @DisplayName("tenant override varsa onu döndürür")
    void should_return_tenant_override_when_present() {
      var override = makeValue("Özel Başlık - Tenant Override");
      when(translationValueRepo.findByKeyCodeAndLocaleAndTenant(KEY_CODE, "TR", TENANT_ID))
          .thenReturn(Optional.of(override));

      String result = translationService.translate(TENANT_ID, "TR", KEY_CODE);

      assertThat(result).isEqualTo("Özel Başlık - Tenant Override");
      verify(translationValueRepo, never()).findSystemDefault(any(), any());
    }

    @Test
    @DisplayName("tenant override yoksa sistem çevirisini döndürür")
    void should_return_system_translation_when_no_override() {
      when(translationValueRepo.findByKeyCodeAndLocaleAndTenant(KEY_CODE, "TR", TENANT_ID))
          .thenReturn(Optional.empty());
      var sysValue = makeValue("İş Emri Onay Bekliyor");
      when(translationValueRepo.findSystemDefault(KEY_CODE, "TR"))
          .thenReturn(Optional.of(sysValue));

      String result = translationService.translate(TENANT_ID, "TR", KEY_CODE);

      assertThat(result).isEqualTo("İş Emri Onay Bekliyor");
    }

    @Test
    @DisplayName("TR çevirisi yoksa EN fallback döner")
    void should_fallback_to_en_when_locale_missing() {
      when(translationValueRepo.findByKeyCodeAndLocaleAndTenant(KEY_CODE, "DE", TENANT_ID))
          .thenReturn(Optional.empty());
      when(translationValueRepo.findSystemDefault(KEY_CODE, "DE")).thenReturn(Optional.empty());
      var enValue = makeValue("Work Order Pending Approval");
      when(translationValueRepo.findSystemDefault(KEY_CODE, "EN")).thenReturn(Optional.of(enValue));

      String result = translationService.translate(TENANT_ID, "DE", KEY_CODE);

      assertThat(result).isEqualTo("Work Order Pending Approval");
    }

    @Test
    @DisplayName("hiç çeviri yoksa TranslationKey.defaultValue döner")
    void should_return_default_value_when_no_translation() {
      when(translationValueRepo.findByKeyCodeAndLocaleAndTenant(anyString(), anyString(), any()))
          .thenReturn(Optional.empty());
      when(translationValueRepo.findSystemDefault(anyString(), anyString()))
          .thenReturn(Optional.empty());
      var key = makeKey(KEY_CODE, "Default EN Title");
      when(translationKeyRepo.findByKeyCode(KEY_CODE)).thenReturn(Optional.of(key));

      String result = translationService.translate(TENANT_ID, "TR", KEY_CODE);

      assertThat(result).isEqualTo("Default EN Title");
    }

    @Test
    @DisplayName("key hiç yoksa keyCode döner")
    void should_return_key_code_when_key_not_found() {
      when(translationValueRepo.findByKeyCodeAndLocaleAndTenant(anyString(), anyString(), any()))
          .thenReturn(Optional.empty());
      when(translationValueRepo.findSystemDefault(anyString(), anyString()))
          .thenReturn(Optional.empty());
      when(translationKeyRepo.findByKeyCode(KEY_CODE)).thenReturn(Optional.empty());

      String result = translationService.translate(TENANT_ID, "TR", KEY_CODE);

      assertThat(result).isEqualTo(KEY_CODE);
    }

    @Test
    @DisplayName("null locale EN olarak işlenir")
    void should_treat_null_locale_as_en() {
      when(translationValueRepo.findByKeyCodeAndLocaleAndTenant(KEY_CODE, "EN", TENANT_ID))
          .thenReturn(Optional.empty());
      var enValue = makeValue("Work Order Pending");
      when(translationValueRepo.findSystemDefault(KEY_CODE, "EN")).thenReturn(Optional.of(enValue));

      String result = translationService.translate(TENANT_ID, null, KEY_CODE);

      assertThat(result).isEqualTo("Work Order Pending");
    }
  }

  @Nested
  @DisplayName("translateAndRender()")
  class TranslateAndRenderTests {

    @Test
    @DisplayName("parametreler doğru şekilde replace edilir")
    void should_replace_all_parameters() {
      when(translationValueRepo.findByKeyCodeAndLocaleAndTenant(KEY_CODE, "TR", TENANT_ID))
          .thenReturn(Optional.empty());
      var tv = makeValue("İş emri {workOrderNumber} onay bekliyor");
      when(translationValueRepo.findSystemDefault(KEY_CODE, "TR")).thenReturn(Optional.of(tv));

      String result =
          translationService.translateAndRender(
              TENANT_ID, "TR", KEY_CODE, Map.of("workOrderNumber", "WO-001-2026"));

      assertThat(result).isEqualTo("İş emri WO-001-2026 onay bekliyor");
    }

    @Test
    @DisplayName("boş params map ile düz metin döner")
    void should_return_plain_text_when_no_params() {
      when(translationValueRepo.findByKeyCodeAndLocaleAndTenant(KEY_CODE, "TR", TENANT_ID))
          .thenReturn(Optional.empty());
      var tv = makeValue("Onay Gerekiyor");
      when(translationValueRepo.findSystemDefault(KEY_CODE, "TR")).thenReturn(Optional.of(tv));

      String result = translationService.translateAndRender(TENANT_ID, "TR", KEY_CODE, null);

      assertThat(result).isEqualTo("Onay Gerekiyor");
    }

    @Test
    @DisplayName("null parametre değeri boş string ile replace edilir")
    void should_replace_null_param_with_empty_string() {
      when(translationValueRepo.findByKeyCodeAndLocaleAndTenant(KEY_CODE, "EN", TENANT_ID))
          .thenReturn(Optional.empty());
      var tv = makeValue("Batch {batchCode} failed");
      when(translationValueRepo.findSystemDefault(KEY_CODE, "EN")).thenReturn(Optional.of(tv));

      Map<String, String> params = new java.util.HashMap<>();
      params.put("batchCode", null);

      String result = translationService.translateAndRender(TENANT_ID, "EN", KEY_CODE, params);

      assertThat(result).isEqualTo("Batch  failed");
    }
  }
}
