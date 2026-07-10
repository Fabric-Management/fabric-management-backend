package com.fabricmanagement.common.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(
    classes = {
      AsyncConfig.class,
      TenantContextAccessor.class,
      AsyncTenantContextPropagationTest.ProbeConfig.class
    })
class AsyncTenantContextPropagationTest {

  @Autowired private AsyncTenantProbe probe;

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  void asyncMethodReceivesTenantContextFromSubmittingThread() throws Exception {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);

    AsyncTenantSnapshot snapshot = probe.captureTenant().get(5, TimeUnit.SECONDS);

    assertThat(snapshot.threadName()).startsWith("async-tenant-");
    assertThat(snapshot.tenantId()).isEqualTo(tenantId);
  }

  @Configuration
  static class ProbeConfig {
    @Bean
    AsyncTenantProbe asyncTenantProbe() {
      return new AsyncTenantProbe();
    }
  }

  public static class AsyncTenantProbe {
    @Async
    public CompletableFuture<AsyncTenantSnapshot> captureTenant() {
      return CompletableFuture.completedFuture(
          new AsyncTenantSnapshot(
              Thread.currentThread().getName(), TenantContext.getCurrentTenantIdOrNull()));
    }
  }

  record AsyncTenantSnapshot(String threadName, UUID tenantId) {}
}
