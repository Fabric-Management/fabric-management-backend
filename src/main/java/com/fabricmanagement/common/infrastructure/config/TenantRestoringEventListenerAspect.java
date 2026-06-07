package com.fabricmanagement.common.infrastructure.config;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Aspect to automatically restore the {@link TenantContext} during event listener execution.
 *
 * <p>When a DomainEvent is processed asynchronously or delayed (e.g., via a scheduler), the
 * original thread's TenantContext is lost. This aspect intercepts
 * {@code @TransactionalEventListener} and {@code @EventListener} methods, extracts the {@code
 * tenantId} from the {@link DomainEvent} payload, and sets the TenantContext before execution. It
 * restores or clears the context afterward.
 */
@Aspect
@Component
@Order(
    Ordered
        .HIGHEST_PRECEDENCE) // Must run BEFORE @Transactional so TenantConnectionProvider sees the
// correct tenant
@Slf4j
public class TenantRestoringEventListenerAspect {

  @Around(
      "@annotation(org.springframework.transaction.event.TransactionalEventListener) "
          + "|| @annotation(org.springframework.context.event.EventListener)")
  public Object restoreTenantContext(ProceedingJoinPoint pjp) throws Throwable {
    Object[] args = pjp.getArgs();
    UUID eventTenantId = extractTenantId(args);

    UUID previousTenantId = TenantContext.getCurrentTenantIdOrNull();
    boolean restored = false;

    try {
      if (eventTenantId != null && !eventTenantId.equals(previousTenantId)) {
        TenantContext.setCurrentTenantId(eventTenantId);
        restored = true;
        log.debug(
            "Aspect restored TenantContext to {} for {}",
            eventTenantId,
            pjp.getSignature().toShortString());
      } else if (eventTenantId == null) {
        log.warn(
            "Intercepted async event listener {} but payload does not contain a DomainEvent with tenantId. Aspect skipping tenant restoration.",
            pjp.getSignature().toShortString());
      }
      return pjp.proceed();
    } finally {
      if (restored) {
        if (previousTenantId == null) {
          TenantContext.clear();
        } else {
          TenantContext.setCurrentTenantId(previousTenantId);
        }
      }
    }
  }

  private UUID extractTenantId(Object[] args) {
    for (Object arg : args) {
      if (arg instanceof DomainEvent event) {
        return event.getTenantId();
      }
    }
    return null;
  }
}
