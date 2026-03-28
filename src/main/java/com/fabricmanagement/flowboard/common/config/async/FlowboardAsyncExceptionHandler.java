package com.fabricmanagement.flowboard.common.config.async;

import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

/**
 * Phase 4: @Async AFTER_COMMIT Hata Yönetimi
 *
 * <p>Async execution esnasında yakalanamayan exception'ları (örn. SmartTaskGeneratorListener,
 * TaskEventListener) loglar/alert mekanizmasına iletir. Outbox pattern öncesi tolere edilemez event
 * kayıplarını tespit etmek amacıyla konumlandırılmıştır.
 */
@Slf4j
public class FlowboardAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

  @Override
  public void handleUncaughtException(Throwable ex, Method method, Object... params) {
    log.error("==========================================");
    log.error("ASYNC UNCAUGHT EXCEPTION DETECTED");
    log.error("Method    : {}", method.getName());
    log.error("Exception : {}", ex.getMessage());
    log.error("Cause     : {}", ex.getCause() != null ? ex.getCause().getMessage() : "N/A");

    // Parametreleri logla
    for (int i = 0; i < params.length; i++) {
      log.error("Parameter[{}] : {}", i, params[i]);
    }
    log.error("==========================================");

    // TODO (Faz 4.2 - Technical Debt):
    // Buradan Slack/Alerting webhook'larına entegrasyon eklenebilir.
    // İlk etapta bu logları/metrikleri en az bir hafta production'da izle.
    // Eğer tolere edilemez seviyede kritik event kaybı yaşanıyorsa (listener fail olup event uçar
    // ve DB'de izi kalmazsa),
    // bu basit Async handler yerine Idempotency korumalı "Outbox Pattern" tablosu (retry/polling
    // altyapısı ile) kurulmalıdır.
  }
}
