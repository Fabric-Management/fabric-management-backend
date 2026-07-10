package com.fabricmanagement.common.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.modulith.events.ApplicationModuleListener;

class TenantRestoringEventListenerAspectTest {

  @Test
  void pointcutMatchesCurrentApplicationModuleListenerPackage() throws NoSuchMethodException {
    AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
    pointcut.setExpression(
        "@annotation(org.springframework.transaction.event.TransactionalEventListener) "
            + "|| @annotation(org.springframework.context.event.EventListener) "
            + "|| @annotation(org.springframework.modulith.events.ApplicationModuleListener)");

    Method method = ModulithListenerProbe.class.getMethod("handle");

    assertThat(pointcut.matches(method, ModulithListenerProbe.class)).isTrue();
  }

  static class ModulithListenerProbe {
    @ApplicationModuleListener
    public void handle() {}
  }
}
