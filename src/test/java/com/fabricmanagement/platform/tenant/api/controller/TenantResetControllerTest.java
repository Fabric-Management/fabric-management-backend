package com.fabricmanagement.platform.tenant.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.tenant.app.TenantResetService;
import com.fabricmanagement.platform.tenant.dto.ResetDemoResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TenantResetControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Mock private TenantResetService tenantResetService;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldResetDemoAndReturnWrappedSummary() {
    TenantResetController controller = new TenantResetController(tenantResetService);
    AuthenticatedUserContext ctx =
        new AuthenticatedUserContext(USER_ID, "ADMIN", List.of(), null, TENANT_ID);
    TestingAuthenticationToken authentication =
        new TestingAuthenticationToken("principal", "credentials");
    authentication.setDetails(ctx);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    ResetDemoResponse resetResponse =
        new ResetDemoResponse(TENANT_ID, true, 15, Map.of("sales.quote", 2));
    when(tenantResetService.reset(ctx)).thenReturn(resetResponse);

    ResponseEntity<ApiResponse<ResetDemoResponse>> response = controller.resetDemo();

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isTrue();
    assertThat(response.getBody().getData()).isEqualTo(resetResponse);
    assertThat(response.getBody().getMessage()).isEqualTo("Demo reset with fresh sample data");
    verify(tenantResetService).reset(ctx);
  }
}
