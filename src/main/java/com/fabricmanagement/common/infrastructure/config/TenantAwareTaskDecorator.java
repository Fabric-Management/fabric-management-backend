package com.fabricmanagement.common.infrastructure.config;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * Propagates {@link TenantContext} (tenantId, tenantUid, userId, country) from the calling thread
 * to the async worker thread and clears it after execution.
 *
 * <p>Without this decorator, {@code @Async} methods executed in a thread-pool would lose the tenant
 * context because {@link ThreadLocal} values are not inherited by pool threads.
 *
 * @see AsyncConfig
 * @see TenantContext
 */
@Slf4j
public class TenantAwareTaskDecorator implements TaskDecorator {

  @Override
  @NonNull
  public Runnable decorate(@NonNull Runnable runnable) {
    UUID tenantId = TenantContext.getCurrentTenantIdOrNull();
    String tenantUid = TenantContext.getCurrentTenantUid();
    UUID userId = TenantContext.getCurrentUserId();
    String country = TenantContext.getCurrentTenantCountry();

    if (tenantId == null) {
      log.warn(
          "TenantAwareTaskDecorator: No tenantId in ThreadLocal at capture time. "
              + "Async task will rely on event-payload restoration via aspect.");
    }

    return () -> {
      try {
        if (tenantId != null) {
          TenantContext.setCurrentTenantId(tenantId);
        }
        if (tenantUid != null) {
          TenantContext.setCurrentTenantUid(tenantUid);
        }
        if (userId != null) {
          TenantContext.setCurrentUserId(userId);
        }
        if (country != null) {
          TenantContext.setCurrentTenantCountry(country);
        }
        runnable.run();
      } finally {
        TenantContext.clear();
      }
    };
  }
}
