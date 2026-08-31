package com.fabricmanagement.product.fiber.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.audit.app.AuditService;
import com.fabricmanagement.product.fiber.domain.event.FiberMaterialSourceDeclaredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Writes a material-source audit only after the declaring transaction has committed. */
@Component
@RequiredArgsConstructor
public class FiberMaterialSourceAuditListener {

  private final AuditService auditService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMaterialSourceDeclared(FiberMaterialSourceDeclaredEvent event) {
    TenantContext.TenantSnapshot previous = TenantContext.capture();

    try {
      TenantContext.restore(
          new TenantContext.TenantSnapshot(event.getTenantId(), null, event.getActorId(), null));
      auditService.logAction(
          "FIBER_MATERIAL_SOURCE_DECLARED",
          "fiber",
          event.getFiberId().toString(),
          "Material source declared: " + event.getOldValue() + " -> " + event.getNewValue());
    } finally {
      TenantContext.restore(previous);
    }
  }
}
