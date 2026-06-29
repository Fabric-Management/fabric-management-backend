package com.fabricmanagement.common.infrastructure.web.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class TenantReadOnlyExceptionHandlerTest {

  @Test
  @DisplayName("handler emits 403 TENANT_READ_ONLY")
  void shouldEmitTenantReadOnlyProblemDetail() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler(null);
    HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/v1/orders");

    ApiProblemDetail response =
        handler.handleTenantReadOnly(new TenantReadOnlyException(), request);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getCode()).isEqualTo("TENANT_READ_ONLY");
    assertThat(response.getDetail()).isEqualTo(TenantReadOnlyException.MESSAGE);
  }

  @Test
  @DisplayName("handler emits 403 DEMO_MODE_REQUIRED")
  void shouldEmitDemoModeRequiredProblemDetail() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler(null);
    HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/v1/playground/personas");

    ResponseEntity<ApiProblemDetail> response =
        handler.handleDomain(
            new PlatformDomainException(
                "Demo mode is required to use playground impersonation", "DEMO_MODE_REQUIRED", 403),
            request);

    assertThat(response.getStatusCode().value()).isEqualTo(403);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getCode()).isEqualTo("DEMO_MODE_REQUIRED");
    assertThat(response.getBody().getDetail())
        .isEqualTo("Demo mode is required to use playground impersonation");
  }
}
