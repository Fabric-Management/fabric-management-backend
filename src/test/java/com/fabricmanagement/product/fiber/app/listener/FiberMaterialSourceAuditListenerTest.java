package com.fabricmanagement.product.fiber.app.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.audit.app.AuditService;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.event.FiberMaterialSourceDeclaredEvent;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class FiberMaterialSourceAuditListenerTest {

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void listenerUsesEventTenantAndActorThenRestoresPreviousContext() {
    UUID previousTenant = UUID.randomUUID();
    UUID previousActor = UUID.randomUUID();
    UUID eventTenant = UUID.randomUUID();
    UUID declaringActor = UUID.randomUUID();
    UUID fiberId = UUID.randomUUID();
    TenantContext.restore(
        new TenantContext.TenantSnapshot(previousTenant, "PREVIOUS", previousActor, "TR"));

    AtomicReference<TenantContext.TenantSnapshot> auditContext = new AtomicReference<>();
    AuditService auditService = mock(AuditService.class);
    doAnswer(
            invocation -> {
              auditContext.set(TenantContext.capture());
              return null;
            })
        .when(auditService)
        .logAction(
            "FIBER_MATERIAL_SOURCE_DECLARED",
            "fiber",
            fiberId.toString(),
            "Material source declared: null -> RECYCLED");

    new FiberMaterialSourceAuditListener(auditService)
        .onMaterialSourceDeclared(
            new FiberMaterialSourceDeclaredEvent(
                eventTenant, fiberId, null, MaterialSource.RECYCLED, declaringActor));

    assertThat(auditContext.get().tenantId()).isEqualTo(eventTenant);
    assertThat(auditContext.get().userId()).isEqualTo(declaringActor);
    assertThat(TenantContext.capture())
        .isEqualTo(
            new TenantContext.TenantSnapshot(previousTenant, "PREVIOUS", previousActor, "TR"));
    verify(auditService)
        .logAction(
            "FIBER_MATERIAL_SOURCE_DECLARED",
            "fiber",
            fiberId.toString(),
            "Material source declared: null -> RECYCLED");
  }

  @Test
  void listenerIsAfterCommit() throws Exception {
    TransactionalEventListener annotation =
        FiberMaterialSourceAuditListener.class
            .getDeclaredMethod("onMaterialSourceDeclared", FiberMaterialSourceDeclaredEvent.class)
            .getAnnotation(TransactionalEventListener.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
  }
}
