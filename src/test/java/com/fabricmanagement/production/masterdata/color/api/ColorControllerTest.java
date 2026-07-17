package com.fabricmanagement.production.masterdata.color.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.production.masterdata.color.app.ColorService;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.domain.ColorCardSpec;
import com.fabricmanagement.production.masterdata.color.domain.ColorFamily;
import com.fabricmanagement.production.masterdata.color.domain.ColorStandardStatus;
import com.fabricmanagement.production.masterdata.color.domain.ColorType;
import com.fabricmanagement.production.masterdata.color.domain.exception.ColorDomainException;
import com.fabricmanagement.production.masterdata.color.mapper.ColorMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ColorController.class)
@EnableMethodSecurity
class ColorControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID COLOR_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private ColorService colorService;
  @MockBean private ColorMapper colorMapper;
  @MockBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  private final ColorMapper delegateMapper = Mappers.getMapper(ColorMapper.class);

  @BeforeEach
  void mapWithRealMapper() {
    when(colorMapper.toDto(any(Color.class)))
        .thenAnswer(invocation -> delegateMapper.toDto(invocation.getArgument(0)));
  }

  @Test
  @WithMockUser
  void listReturnsPaginationEnvelopeAndForwardsEveryFilter() throws Exception {
    allow("read");
    Color color = color("NAVY-01", "Navy");
    Color secondColor = Color.create(TENANT_ID, "NAVY-02", "Navy Two", "#202A44");
    secondColor.setId(UUID.fromString("33333333-3333-4333-8333-333333333333"));
    PageRequest requestedPage = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "code"));
    when(colorService.list(
            eq("navy_01"),
            eq(ColorType.DYED),
            eq(ColorFamily.BLUE),
            eq(ColorStandardStatus.DRAFT),
            eq(true),
            any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(color, secondColor), requestedPage, 7));

    mockMvc
        .perform(
            get("/api/v1/production/colors")
                .param("q", "navy_01")
                .param("colorType", "DYED")
                .param("colorFamily", "BLUE")
                .param("standardStatus", "DRAFT")
                .param("includeInactive", "true")
                .param("page", "1")
                .param("size", "5")
                .param("sort", "code,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].code").value("NAVY-01"))
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.size").value(5))
        .andExpect(jsonPath("$.data.totalElements").value(7))
        .andExpect(jsonPath("$.data.totalPages").value(2));

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(colorService)
        .list(
            eq("navy_01"),
            eq(ColorType.DYED),
            eq(ColorFamily.BLUE),
            eq(ColorStandardStatus.DRAFT),
            eq(true),
            pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    assertThat(pageableCaptor.getValue().getSort().getOrderFor("code").getDirection())
        .isEqualTo(Sort.Direction.DESC);
  }

  @Test
  @WithMockUser
  void listUsesInactiveFalseAndCodeSortDefaults() throws Exception {
    allow("read");
    when(colorService.list(isNull(), isNull(), isNull(), isNull(), eq(false), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/api/v1/production/colors"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isArray());

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(colorService)
        .list(isNull(), isNull(), isNull(), isNull(), eq(false), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    assertThat(pageableCaptor.getValue().getSort().getOrderFor("code")).isNotNull();
  }

  @Test
  @WithMockUser
  void putIsFullReplacementAndPatchIsNotExposed() throws Exception {
    allow("write");
    when(colorService.update(eq(COLOR_ID), any(ColorCardSpec.class)))
        .thenAnswer(
            invocation -> {
              Color updated = Color.create(TENANT_ID, invocation.getArgument(1));
              updated.setId(COLOR_ID);
              return updated;
            });

    String responseBody =
        mockMvc
            .perform(
                put("/api/v1/production/colors/{id}", COLOR_ID)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"code":"navy-02","name":"Replacement"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value("NAVY-02"))
            .andExpect(jsonPath("$.data.colorType").value("DYED"))
            .andExpect(jsonPath("$.data.colorFamily").value("UNDEFINED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode responseData = objectMapper.readTree(responseBody).path("data");
    assertThat(responseData.has("notes")).isTrue();
    assertThat(responseData.get("notes").isNull()).isTrue();
    assertThat(responseData.has("colorHex")).isTrue();
    assertThat(responseData.get("colorHex").isNull()).isTrue();
    assertThat(responseData.has("pantoneCode")).isTrue();
    assertThat(responseData.get("pantoneCode").isNull()).isTrue();

    ArgumentCaptor<ColorCardSpec> specCaptor = ArgumentCaptor.forClass(ColorCardSpec.class);
    verify(colorService).update(eq(COLOR_ID), specCaptor.capture());
    assertThat(specCaptor.getValue().notes()).isNull();
    assertThat(specCaptor.getValue().colorHex()).isNull();
    assertThat(specCaptor.getValue().pantoneCode()).isNull();

    mockMvc
        .perform(
            patch("/api/v1/production/colors/{id}", COLOR_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"NAVY-02","name":"Replacement"}
                    """))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", containsString("PUT")))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  @WithMockUser
  void invalidHexAndBlankCodeUseGlobalValidationContract() throws Exception {
    allow("write");

    mockMvc
        .perform(
            post("/api/v1/production/colors")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":" ","name":"Navy","colorHex":"blue"}
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.code").exists())
        .andExpect(jsonPath("$.errors.colorHex").exists());
  }

  @Test
  @WithMockUser
  void duplicateCodeAndApprovedStandardMutationReturnConflict() throws Exception {
    allow("write");
    when(colorService.create(any(ColorCardSpec.class)))
        .thenThrow(ColorDomainException.duplicateCode("NAVY-01"));

    mockMvc
        .perform(
            post("/api/v1/production/colors")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"NAVY-01","name":"Navy"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRODUCTION_COLOR_DUPLICATE_CODE"));

    when(colorService.update(eq(COLOR_ID), any(ColorCardSpec.class)))
        .thenThrow(ColorDomainException.approvedStandardIsImmutable("NAVY-01"));

    mockMvc
        .perform(
            put("/api/v1/production/colors/{id}", COLOR_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"NAVY-01","name":"Changed"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRODUCTION_COLOR_STANDARD_APPROVED"));
  }

  @Test
  @WithMockUser
  void colorsReadPermitsBothReadEndpoints() throws Exception {
    allow("read");
    when(colorService.list(isNull(), isNull(), isNull(), isNull(), eq(false), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    when(colorService.findById(eq(COLOR_ID))).thenReturn(color("NAVY-01", "Navy"));

    mockMvc.perform(get("/api/v1/production/colors")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/production/colors/{id}", COLOR_ID)).andExpect(status().isOk());
  }

  @Test
  @WithMockUser
  void colorsWritePermitsCreateAndReplace() throws Exception {
    allow("write");
    when(colorService.create(any(ColorCardSpec.class))).thenReturn(color("NAVY-01", "Navy"));
    when(colorService.update(eq(COLOR_ID), any(ColorCardSpec.class)))
        .thenReturn(color("NAVY-01", "Navy"));

    mockMvc
        .perform(
            post("/api/v1/production/colors")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"NAVY-01","name":"Navy"}
                    """))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            put("/api/v1/production/colors/{id}", COLOR_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"NAVY-01","name":"Navy"}
                    """))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser
  void colorsApprovePermitsApproveAndRevert() throws Exception {
    allow("approve");
    when(colorService.approve(eq(COLOR_ID))).thenReturn(color("NAVY-01", "Navy"));
    when(colorService.revertToDraft(eq(COLOR_ID))).thenReturn(color("NAVY-01", "Navy"));

    mockMvc
        .perform(post("/api/v1/production/colors/{id}/approve", COLOR_ID).with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/production/colors/{id}/revert-to-draft", COLOR_ID).with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser
  void colorsManagePermitsActivateAndDeactivate() throws Exception {
    allow("manage");
    when(colorService.activate(eq(COLOR_ID))).thenReturn(color("NAVY-01", "Navy"));
    when(colorService.deactivate(eq(COLOR_ID))).thenReturn(color("NAVY-01", "Navy"));

    mockMvc
        .perform(post("/api/v1/production/colors/{id}/activate", COLOR_ID).with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/production/colors/{id}/deactivate", COLOR_ID).with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser
  void oneColourActionDoesNotImplyAnother() throws Exception {
    // colors:write must not confer approve or manage authority.
    allow("write");

    mockMvc
        .perform(post("/api/v1/production/colors/{id}/approve", COLOR_ID).with(csrf()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/v1/production/colors/{id}/activate", COLOR_ID).with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void legacyProductsPermissionGrantsNoColourAuthority() throws Exception {
    // Clean cutover: a caller who still holds the old broad products grant is fully locked out.
    when(authEvaluator.can(any(Authentication.class), eq("products"), eq("read"))).thenReturn(true);
    when(authEvaluator.can(any(Authentication.class), eq("products"), eq("write")))
        .thenReturn(true);
    // No colors:* stubbed -> every colours action resolves false.

    mockMvc.perform(get("/api/v1/production/colors")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/api/v1/production/colors/{id}", COLOR_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"NAVY-01","name":"Navy"}
                    """))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/v1/production/colors/{id}/approve", COLOR_ID).with(csrf()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/v1/production/colors/{id}/deactivate", COLOR_ID).with(csrf()))
        .andExpect(status().isForbidden());
  }

  private void allow(String action) {
    when(authEvaluator.can(any(Authentication.class), eq("colors"), eq(action))).thenReturn(true);
  }

  private Color color(String code, String name) {
    Color color = Color.create(TENANT_ID, code, name, "#1F2A44");
    color.setId(COLOR_ID);
    return color;
  }
}
