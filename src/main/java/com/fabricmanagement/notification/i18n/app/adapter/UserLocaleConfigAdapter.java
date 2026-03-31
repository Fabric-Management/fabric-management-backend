package com.fabricmanagement.notification.i18n.app.adapter;

import com.fabricmanagement.notification.i18n.domain.UserLocaleConfig;
import com.fabricmanagement.notification.i18n.infra.repository.UserLocaleConfigRepository;
import com.fabricmanagement.platform.user.domain.port.UserLocaleConfigPort;
import com.fabricmanagement.platform.user.domain.port.UserLocalePreferences;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserLocaleConfigAdapter implements UserLocaleConfigPort {

  private final UserLocaleConfigRepository repository;

  @Override
  public Optional<UserLocalePreferences> findByUserId(UUID userId) {
    return repository
        .findByUserId(userId)
        .map(cfg -> new UserLocalePreferences(cfg.getUserId(), cfg.getLocale(), cfg.getTimezone()));
  }

  @Override
  @Transactional
  public void saveOrUpdate(UUID tenantId, UUID userId, String locale, String timezone) {
    repository
        .findByUserId(userId)
        .ifPresentOrElse(
            cfg -> {
              // If locale is null, keep existing locale
              String l = locale != null ? locale : cfg.getLocale();
              // Pass existing dateFormat to avoid nulling it out
              cfg.updateLocale(l, cfg.getDateFormat(), timezone);
              repository.save(cfg);
            },
            () -> {
              // Fallback to "EN" if creating for the first time and locale is null
              String l = locale != null ? locale : "EN";
              var cfg = UserLocaleConfig.create(tenantId, userId, l);
              // Update remaining fields that create() does not accept
              cfg.updateLocale(l, null, timezone);
              repository.save(cfg);
            });
  }

  @Override
  @Transactional
  public void deleteByUserId(UUID userId) {
    repository.findByUserId(userId).ifPresent(repository::delete);
  }
}
