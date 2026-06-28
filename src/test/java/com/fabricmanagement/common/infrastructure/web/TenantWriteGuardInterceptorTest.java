package com.fabricmanagement.common.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.common.infrastructure.web.exception.TenantReadOnlyException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantWriteGuardInterceptorTest {

  private final TenantAccessPort tenantAccessPort =
      org.mockito.Mockito.mock(TenantAccessPort.class);
  private final TrialReadOnlyProperties properties = new TrialReadOnlyProperties();
  private final TenantWriteGuardInterceptor interceptor =
      new TenantWriteGuardInterceptor(Optional.of(tenantAccessPort), Optional.of(properties));

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("EXPIRED tenant write is rejected with TENANT_READ_ONLY")
  void shouldRejectExpiredTenantWrite() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    when(tenantAccessPort.isWritable(tenantId)).thenReturn(false);

    assertThatThrownBy(() -> preHandle("POST", "/api/v1/orders"))
        .isInstanceOf(TenantReadOnlyException.class)
        .hasMessage(TenantReadOnlyException.MESSAGE);
  }

  @Test
  @DisplayName("EXPIRED tenant GET passes through")
  void shouldAllowExpiredTenantRead() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);

    assertThatCode(() -> assertThat(preHandle("GET", "/api/v1/orders")).isTrue())
        .doesNotThrowAnyException();
    verify(tenantAccessPort, never()).isWritable(tenantId);
  }

  @Test
  @DisplayName("EXPIRED tenant whitelisted POST passes through")
  void shouldAllowWhitelistedExpiredTenantWrite() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    properties.setAllowPaths(List.of("/api/v1/subscriptions/*/activate", "/api/v1/auth/**"));

    assertThat(preHandle("POST", "/api/v1/subscriptions/123/activate")).isTrue();
    verify(tenantAccessPort, never()).isWritable(tenantId);
  }

  @Test
  @DisplayName("EXPIRED tenant go-real POST passes through read-only guard")
  void shouldAllowGoRealForExpiredTenantWriteGuard() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);

    assertThat(preHandle("POST", "/api/v1/tenant/go-real")).isTrue();
    verify(tenantAccessPort, never()).isWritable(tenantId);
  }

  @Test
  @DisplayName("TRIAL or ACTIVE tenant write passes through")
  void shouldAllowWritableTenantWrite() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    when(tenantAccessPort.isWritable(tenantId)).thenReturn(true);

    assertThat(preHandle("POST", "/api/v1/orders")).isTrue();
    verify(tenantAccessPort).isWritable(tenantId);
  }

  @Test
  @DisplayName("unauthenticated request passes through")
  void shouldAllowUnauthenticatedRequest() {
    assertThat(preHandle("POST", "/api/v1/orders")).isTrue();
    verify(tenantAccessPort, never()).isWritable(org.mockito.ArgumentMatchers.any());
  }

  private boolean preHandle(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
  }
}
