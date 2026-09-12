package com.fabricmanagement.platform.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.JwtAuthenticationFilter;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.common.infrastructure.security.RestAuthenticationEntryPoint;
import com.fabricmanagement.common.infrastructure.security.SecurityConfig;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.web.LocalizationFilter;
import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.user.app.PermissionCatalogueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PermissionCatalogueController.class)
@ActiveProfiles("test")
@Import({
  PermissionCatalogueService.class,
  SecurityConfig.class,
  RestAuthenticationEntryPoint.class,
  JwtAuthenticationFilter.class,
  LocalizationFilter.class
})
class PermissionCatalogueControllerTest {
  private static final String URL = "/api/v1/platform/permissions/catalogue";
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TenantQueryPort tenantQueryPort;

  @Test
  void anonymousCallerCannotReadTheCatalogue() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedSessionWithoutAuthoritiesReceivesTheWholeOrderedCatalogue() throws Exception {
    String json =
        mvc.perform(get(URL).with(user("ordinary-user").authorities(List.<GrantedAuthority>of())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var entries = mapper.readTree(json).path("data");
    List<String> actual = new ArrayList<>();
    entries.forEach(
        entry -> {
          String resource = entry.path("resource").asText();
          String action = entry.path("action").asText();
          PermissionKey key = PermissionKey.of(resource, action).orElseThrow();
          assertThat(entry.path("key").asText()).isEqualTo(key.key());
          assertThat(entry.has("enforced")).isFalse();
          assertThat(entry.has("enforcedBy")).isFalse();
          assertThat(entry.has("note")).isFalse();
          actual.add(entry.path("key").asText());
        });
    assertThat(actual)
        .containsExactlyElementsOf(
            Arrays.stream(PermissionKey.values()).map(PermissionKey::key).sorted().toList());
  }
}
