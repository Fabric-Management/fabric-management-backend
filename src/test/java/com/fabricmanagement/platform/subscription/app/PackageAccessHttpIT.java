package com.fabricmanagement.platform.subscription.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.subscription.infra.repository.OSDefinitionRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * PACKAGE-ACCESS-3: a package decides real HTTP access. Runs on the catalogue the migrations
 * install (no test catalogue) and the real evaluation commands: owner and persona are served while
 * the package is selected, refused once it is deselected, and served again when reselected.
 */
@AutoConfigureMockMvc
class PackageAccessHttpIT extends AbstractIntegrationTest {

  private static final String YARN_LIST = "/api/v1/production/yarns";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantRepository tenants;
  @Autowired private EvaluationSubscriptionService evaluations;
  @Autowired private EvaluationPackageQueryService catalogue;
  @Autowired private OSDefinitionRepository definitions;

  @MockitoBean private PermissionEvaluator permissionEvaluator;

  private UUID tenantId;
  private final UUID owner = UUID.randomUUID();
  private final UUID persona = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    var tenant = Tenant.create("Hawthorn " + suffix, "HW-" + suffix);
    tenant.setDemoMode(true);
    tenantId = tenants.saveAndFlush(tenant).getId();
    TenantContext.setCurrentTenantId(tenantId);
    evaluations.initialisePlayground(tenantId);
    TenantContext.clear();

    when(permissionEvaluator.evaluate(any(), eq("ADMIN"), any(), any()))
        .thenReturn(new PermissionResult(Map.of(), true));
    when(permissionEvaluator.evaluate(any(), eq("MANAGER"), any(), any()))
        .thenReturn(new PermissionResult(Map.of("yarn", Map.of("read", DataScope.GLOBAL)), false));
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void aCleanInstallOffersEveryPackageAndEverythingStartsOpen() {
    assertThat(catalogue.catalogue().codes())
        .contains("FabricOS", "YarnOS", "AccountOS", "LoomOS", "IntelligenceOS");
    assertThat(definitions.findByOsCode("YarnOS").orElseThrow().getIncludedModules())
        .containsExactly("yarn");
  }

  @Test
  void ownerAndPersonaAreServedWhileThePackageIsSelected() throws Exception {
    performAs(owner, "ADMIN").andExpect(status().isOk());
    performAs(persona, "MANAGER").andExpect(status().isOk());
  }

  @Test
  void deselectingThePackageRefusesBothAndReselectingServesThemAgain() throws Exception {
    selectOnly("FabricOS");

    performAs(owner, "ADMIN")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PACKAGE_NOT_ENABLED"))
        .andExpect(jsonPath("$.args[0]").value("yarn"));
    performAs(persona, "MANAGER")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PACKAGE_NOT_ENABLED"));

    selectOnly("FabricOS", "YarnOS");

    performAs(owner, "ADMIN").andExpect(status().isOk());
    performAs(persona, "MANAGER").andExpect(status().isOk());
  }

  @Test
  void aRoleWithoutPermissionIsStillRefusedByTheRoleWhenThePackageIsSelected() throws Exception {
    when(permissionEvaluator.evaluate(any(), eq("VIEWER"), any(), any()))
        .thenReturn(new PermissionResult(Map.of(), false));

    performAs(UUID.randomUUID(), "VIEWER")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  private void selectOnly(String... codes) {
    TenantContext.setCurrentTenantId(tenantId);
    try {
      evaluations.select(
          tenantId, "PLAYGROUND", false, List.of(codes), catalogue.catalogue().version());
    } finally {
      TenantContext.clear();
    }
  }

  private ResultActions performAs(UUID userId, String role) throws Exception {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
    var context = new AuthenticatedUserContext(userId, role, List.of("PRODUCTION"), null, tenantId);
    var token = UsernamePasswordAuthenticationToken.authenticated(context, "n/a", List.of());
    token.setDetails(context);
    return mockMvc.perform(get(YARN_LIST).with(authentication(token)));
  }
}
