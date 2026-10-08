package com.fabricmanagement.common.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.platform.auth.app.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * CEDIT-05 L13: when a request starts an async response (a live stream), its pooled thread goes
 * back to the container without {@code afterCompletion}; the tenant context must not ride along.
 */
class JwtContextInterceptorAsyncTest {

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("Starting async handling clears the request thread's tenant context")
  void asyncStartClearsTenantContext() {
    JwtContextInterceptor interceptor =
        new JwtContextInterceptor(
            mock(JwtService.class), mock(TenantQueryPort.class), Optional.empty());
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    TenantContext.setCurrentUserId(UUID.randomUUID());

    interceptor.afterConcurrentHandlingStarted(
        new MockHttpServletRequest("GET", "/api/v1/sales/orders/x/live-events"),
        new MockHttpServletResponse(),
        new Object());

    assertThat(TenantContext.isSet()).isFalse();
    assertThat(TenantContext.getCurrentUserId()).isNull();
  }
}
