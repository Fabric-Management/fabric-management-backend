package com.fabricmanagement.notification.i18n.api;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.notification.hub.dto.UpdateLocalePreferenceRequest;
import com.fabricmanagement.notification.i18n.app.TranslationService;
import com.fabricmanagement.notification.i18n.domain.UserLocaleConfig;
import com.fabricmanagement.notification.i18n.infra.repository.TenantLocaleConfigRepository;
import com.fabricmanagement.notification.i18n.infra.repository.UserLocaleConfigRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * i18n Locale Yönetimi API.
 *
 * <p>GET /api/i18n/my-locale — Kullanıcının aktif locale'i PUT /api/i18n/my-locale — Locale
 * tercihini güncelle GET /api/i18n/translate — Tek key çevirisi (test/debug) GET /api/i18n/tenant —
 * Tenant locale ayarları
 */
@RestController
@RequestMapping("/api/i18n")
@RequiredArgsConstructor
@Tag(name = "i18n", description = "Çok dil ve yerelleştirme yönetimi")
public class LocaleController {

  private final TranslationService translationService;
  private final UserLocaleConfigRepository userLocaleConfigRepo;
  private final TenantLocaleConfigRepository tenantLocaleConfigRepo;

  @GetMapping("/my-locale")
  @Operation(summary = "Kullanıcının aktif locale konfigürasyonunu getir")
  public ApiResponse<Map<String, Object>> getMyLocale() {
    var ctx = currentUser();
    String locale = translationService.resolveLocaleForUser(ctx.tenantId(), ctx.userId());

    Map<String, Object> userCfg =
        userLocaleConfigRepo
            .findByUserId(ctx.userId())
            .map(
                cfg ->
                    Map.<String, Object>of(
                        "locale", cfg.getLocale(),
                        "dateFormat", cfg.getDateFormat() != null ? cfg.getDateFormat() : "",
                        "timezone", cfg.getTimezone() != null ? cfg.getTimezone() : ""))
            .orElse(Map.of());

    var response =
        Map.<String, Object>of(
            "userId", ctx.userId(),
            "resolvedLocale", locale,
            "userConfig", userCfg);

    return ApiResponse.success(response);
  }

  @PutMapping("/my-locale")
  @Operation(summary = "Kullanıcının locale tercihini güncelle")
  public ApiResponse<Void> updateMyLocale(@Valid @RequestBody UpdateLocalePreferenceRequest req) {
    var ctx = currentUser();

    userLocaleConfigRepo
        .findByUserId(ctx.userId())
        .ifPresentOrElse(
            cfg -> {
              cfg.updateLocale(req.locale(), req.dateFormat(), req.timezone());
              userLocaleConfigRepo.save(cfg);
            },
            () -> {
              var cfg = UserLocaleConfig.create(ctx.tenantId(), ctx.userId(), req.locale());
              cfg.updateLocale(req.locale(), req.dateFormat(), req.timezone());
              userLocaleConfigRepo.save(cfg);
            });

    return ApiResponse.success(null);
  }

  @GetMapping("/translate")
  @Operation(summary = "Verilen key'i çevir (debug/test amaçlı)")
  public ApiResponse<String> translate(
      @RequestParam String keyCode, @RequestParam(required = false) String locale) {
    var ctx = currentUser();
    String resolvedLocale =
        locale != null
            ? locale
            : translationService.resolveLocaleForUser(ctx.tenantId(), ctx.userId());
    return ApiResponse.success(
        translationService.translate(ctx.tenantId(), resolvedLocale, keyCode));
  }

  @GetMapping("/tenant")
  @Operation(summary = "Tenant'ın locale konfigürasyonunu getir")
  public ApiResponse<Map<String, Object>> getTenantLocale() {
    var ctx = currentUser();
    Map<String, Object> result =
        tenantLocaleConfigRepo
            .findByTenantId(ctx.tenantId())
            .map(
                cfg ->
                    Map.<String, Object>of(
                        "defaultLocale", cfg.getDefaultLocale(),
                        "supportedLocales", cfg.getSupportedLocales(),
                        "dateFormat", cfg.getDateFormat(),
                        "timeFormat", cfg.getTimeFormat(),
                        "timezone", cfg.getTimezone(),
                        "currency", cfg.getCurrency()))
            .orElse(Map.of("defaultLocale", "TR", "supportedLocales", List.of("TR", "EN")));

    return ApiResponse.success(result);
  }

  // ---- Yardımcı ----

  private AuthenticatedUserContext currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof AuthenticatedUserContext ctx) {
      return ctx;
    }
    throw new IllegalStateException("AuthenticatedUserContext not found in SecurityContext");
  }
}
