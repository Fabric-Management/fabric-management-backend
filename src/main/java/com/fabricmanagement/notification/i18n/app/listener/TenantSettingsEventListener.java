package com.fabricmanagement.notification.i18n.app.listener;

import com.fabricmanagement.common.infrastructure.events.TenantSettingsUpdatedEvent;
import com.fabricmanagement.notification.i18n.domain.TenantLocaleConfig;
import com.fabricmanagement.notification.i18n.infra.repository.TenantLocaleConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for TenantSettingsUpdatedEvent from the platform to synchronize local localization
 * config. By using AFTER_COMMIT, we ensure the new settings are safely stored in the platform
 * before we sync them into TenantLocaleConfig.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantSettingsEventListener {

  private final TenantLocaleConfigRepository tenantLocaleConfigRepo;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void onTenantSettingsUpdated(TenantSettingsUpdatedEvent event) {
    log.info("Syncing TenantLocaleConfig for tenantId={}", event.getTenantId());

    tenantLocaleConfigRepo
        .findByTenantId(event.getTenantId())
        .ifPresentOrElse(
            cfg -> {
              cfg.update(
                  event.getLocale() != null ? event.getLocale() : cfg.getDefaultLocale(),
                  cfg.getSupportedLocales(),
                  cfg.getDateFormat(),
                  cfg.getTimeFormat(),
                  event.getTimezone() != null ? event.getTimezone() : cfg.getTimezone(),
                  event.getCurrency() != null ? event.getCurrency() : cfg.getCurrency());
              tenantLocaleConfigRepo.save(cfg);
            },
            () -> {
              // Should not happen theoretically if tenant initialization flows correctly,
              // but in case it's missing, we create one.
              var newCfg = TenantLocaleConfig.createDefault(event.getTenantId());
              newCfg.update(
                  event.getLocale() != null ? event.getLocale() : newCfg.getDefaultLocale(),
                  newCfg.getSupportedLocales(),
                  newCfg.getDateFormat(),
                  newCfg.getTimeFormat(),
                  event.getTimezone() != null ? event.getTimezone() : newCfg.getTimezone(),
                  event.getCurrency() != null ? event.getCurrency() : newCfg.getCurrency());
              tenantLocaleConfigRepo.save(newCfg);
            });
  }
}
