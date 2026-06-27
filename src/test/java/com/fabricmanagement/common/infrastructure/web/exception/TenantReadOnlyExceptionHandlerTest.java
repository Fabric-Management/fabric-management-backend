package com.fabricmanagement.common.infrastructure.web.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
