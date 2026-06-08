package com.fabricmanagement.common.infrastructure.config;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
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
          + "|| @annotation(org.springframework.context.event.EventListener) "
          + "|| @annotation(org.springframework.modulith.ApplicationModuleListener)")
  public Object restoreTenantContext(ProceedingJoinPoint pjp) throws Throwable {
    Object[] args = pjp.getArgs();
    DomainEvent event = extractDomainEvent(args);
    UUID eventTenantId = event != null ? event.getTenantId() : null;
    String eventCorrelationId = event != null ? event.getCorrelationId() : null;

    UUID previousTenantId = TenantContext.getCurrentTenantIdOrNull();
    String previousCorrelationId = MDC.get("correlationId");
    boolean tenantRestored = false;
    boolean correlationRestored = false;

    try {
      if (eventTenantId != null && !eventTenantId.equals(previousTenantId)) {
        TenantContext.setCurrentTenantId(eventTenantId);
        tenantRestored = true;
        log.debug(
            "Aspect restored TenantContext to {} for {}",
            eventTenantId,
            pjp.getSignature().toShortString());
      } else if (eventTenantId == null) {
        log.warn(
            "Intercepted async event listener {} but payload does not contain a DomainEvent with tenantId. Aspect skipping tenant restoration.",
            pjp.getSignature().toShortString());
      }

      if (eventCorrelationId != null && !eventCorrelationId.equals(previousCorrelationId)) {
        MDC.put("correlationId", eventCorrelationId);
        correlationRestored = true;
      }

      return pjp.proceed();
    } finally {
      if (tenantRestored) {
        if (previousTenantId == null) {
          TenantContext.clear();
        } else {
          TenantContext.setCurrentTenantId(previousTenantId);
        }
      }
      if (correlationRestored) {
        if (previousCorrelationId == null) {
          MDC.remove("correlationId");
        } else {
          MDC.put("correlationId", previousCorrelationId);
        }
      }
    }
  }

  private DomainEvent extractDomainEvent(Object[] args) {
    for (Object arg : args) {
      if (arg instanceof DomainEvent event) {
        return event;
      }
    }
    return null;
  }
}
