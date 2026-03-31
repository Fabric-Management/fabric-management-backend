package com.fabricmanagement.notification.i18n.app.adapter;

import com.fabricmanagement.common.infrastructure.locale.LocaleResolutionPort;
import com.fabricmanagement.notification.i18n.infra.repository.TenantLocaleConfigRepository;
import com.fabricmanagement.notification.i18n.infra.repository.UserLocaleConfigRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocaleResolutionAdapter implements LocaleResolutionPort {

  private final UserLocaleConfigRepository userLocaleConfigRepository;
  private final TenantLocaleConfigRepository tenantLocaleConfigRepository;

  @Override
  public Optional<String> findUserLocale(UUID userId) {
    return userLocaleConfigRepository.findByUserId(userId).map(cfg -> cfg.getLocale());
  }

  @Override
  public Optional<String> findUserTimezone(UUID userId) {
    return userLocaleConfigRepository.findByUserId(userId).map(cfg -> cfg.getTimezone());
  }

  @Override
  public Optional<String> findTenantDefaultLocale(UUID tenantId) {
    return tenantLocaleConfigRepository.findByTenantId(tenantId).map(cfg -> cfg.getDefaultLocale());
  }

  @Override
  public Optional<String> findTenantTimezone(UUID tenantId) {
    return tenantLocaleConfigRepository.findByTenantId(tenantId).map(cfg -> cfg.getTimezone());
  }
}
