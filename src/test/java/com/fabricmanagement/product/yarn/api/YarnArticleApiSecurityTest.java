package com.fabricmanagement.product.yarn.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.product.yarn.api.controller.YarnArticleController;
import com.fabricmanagement.product.yarn.app.EndUseCatalogService;
import com.fabricmanagement.product.yarn.app.SpinningSystemCatalogService;
import com.fabricmanagement.product.yarn.app.TestMethodCatalogService;
import com.fabricmanagement.product.yarn.app.YarnArticleService;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.dto.YarnArticleMutationResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(YarnArticleController.class)
@EnableMethodSecurity
class YarnArticleApiSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private YarnArticleService articleService;
  @MockitoBean private SpinningSystemCatalogService spinningSystemService;
  @MockitoBean private EndUseCatalogService endUseService;
  @MockitoBean private TestMethodCatalogService testMethodService;
  @MockitoBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockitoBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockitoBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  @Test
  @WithMockUser
  void representativeReadAndWriteAreForbiddenWithoutTheirPermission() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("yarn"), eq("read"))).thenReturn(false);
    when(authEvaluator.can(any(Authentication.class), eq("yarn"), eq("write"))).thenReturn(false);

    mockMvc.perform(get("/api/v1/production/yarns")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/production/yarns")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void representativeReadAndWriteReachTheControllerWithTheirPermission() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("yarn"), eq("read"))).thenReturn(true);
    when(authEvaluator.can(any(Authentication.class), eq("yarn"), eq("write"))).thenReturn(true);
    when(articleService.list(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(Page.empty());
    when(articleService.createDraftResponse(any(), any(), isNull(), isNull()))
        .thenReturn(new YarnArticleMutationResponse(null, List.of()));

    mockMvc.perform(get("/api/v1/production/yarns")).andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/production/yarns")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody()))
        .andExpect(status().isCreated());
  }

  @Test
  @WithMockUser
  void cataloguePatchRejectsContractAbsentSemanticFields() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("yarn"), eq("write"))).thenReturn(true);

    mockMvc
        .perform(
            patch("/api/v1/production/yarns/spinning-systems/{id}", UUID.randomUUID())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\",\"technologyFamily\":\"ROTOR\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser
  void activationConflictSerializesEveryInvariantId() throws Exception {
    UUID articleId = UUID.randomUUID();
    when(authEvaluator.can(any(Authentication.class), eq("yarn"), eq("write"))).thenReturn(true);
    when(articleService.activateView(articleId))
        .thenThrow(new YarnDomainException(List.of("I7", "I15", "I22"), "activation rejected"));

    mockMvc
        .perform(post("/api/v1/production/yarns/{id}/activate", articleId).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.invariantIds[0]").value("I7"))
        .andExpect(jsonPath("$.invariantIds[1]").value("I15"))
        .andExpect(jsonPath("$.invariantIds[2]").value("I22"));
  }

  private String createBody() {
    return "{\"productId\":\"%s\",\"name\":\"Draft yarn\"}".formatted(UUID.randomUUID());
  }
}
