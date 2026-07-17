package com.fabricmanagement.production.masterdata.color.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.color.app.ColorPartnerRefQueryService;
import com.fabricmanagement.production.masterdata.color.app.ColorPartnerRefService;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerCode;
import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerRef;
import com.fabricmanagement.production.masterdata.color.domain.PartnerRole;
import com.fabricmanagement.production.masterdata.color.domain.exception.ColorPartnerRefDomainException;
import com.fabricmanagement.production.masterdata.color.dto.ColorPartnerReverseResolutionDto;
import com.fabricmanagement.production.masterdata.color.dto.CreateColorPartnerRefRequest;
import com.fabricmanagement.production.masterdata.color.mapper.ColorMapper;
import com.fabricmanagement.production.masterdata.color.mapper.ColorPartnerRefMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ColorPartnerRefController.class)
@EnableMethodSecurity
class ColorPartnerRefControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID COLOR_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID PARTNER_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final UUID REF_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

  @Autowired private MockMvc mockMvc;

  @MockBean private ColorPartnerRefService colorPartnerRefService;
  @MockBean private ColorPartnerRefQueryService colorPartnerRefQueryService;
  @MockBean private ColorPartnerRefMapper colorPartnerRefMapper;
  @MockBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  private final ColorPartnerRefMapper delegateMapper =
      Mappers.getMapper(ColorPartnerRefMapper.class);
  private final ColorMapper colorMapper = Mappers.getMapper(ColorMapper.class);

  @BeforeEach
  void useRealMapper() {
    when(colorPartnerRefMapper.toDto(any(ColorPartnerRef.class)))
        .thenAnswer(
            invocation -> delegateMapper.toDto(invocation.getArgument(0, ColorPartnerRef.class)));
    when(colorPartnerRefMapper.toDto(any(ColorPartnerCode.class)))
        .thenAnswer(
            invocation -> delegateMapper.toDto(invocation.getArgument(0, ColorPartnerCode.class)));
  }

  @Test
  @WithMockUser
  void readPermissionAllowsLookupsButDoesNotAllowMutations() throws Exception {
    allow("read");
    Color color = Color.create(TENANT_ID, "NAVY-001", "Navy", "#1F2A44");
    color.setId(COLOR_ID);
    when(colorPartnerRefQueryService.resolve(PARTNER_ID, PartnerRole.CUSTOMER, "ALIAS"))
        .thenReturn(
            new ColorPartnerReverseResolutionDto(
                colorMapper.toDto(color), "ALIAS", null, false, "PRIMARY", REF_ID));

    mockMvc
        .perform(
            get("/api/v1/production/color-partner-refs/resolve")
                .param("partnerId", PARTNER_ID.toString())
                .param("role", "CUSTOMER")
                .param("externalCode", "ALIAS"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/production/colors/{colorId}/partner-refs", COLOR_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void reverseAliasHitReturnsTheCompleteD6Shape() throws Exception {
    allow("read");
    Color color = Color.create(TENANT_ID, "NAVY-001", "Navy", "#1F2A44");
    color.setId(COLOR_ID);
    when(colorPartnerRefQueryService.resolve(PARTNER_ID, PartnerRole.CUSTOMER, "ss24-nvy"))
        .thenReturn(
            new ColorPartnerReverseResolutionDto(
                colorMapper.toDto(color), "SS24-NVY", "Old navy", false, "SS26-NVY-07", REF_ID));

    mockMvc
        .perform(
            get("/api/v1/production/color-partner-refs/resolve")
                .param("partnerId", PARTNER_ID.toString())
                .param("role", "CUSTOMER")
                .param("externalCode", "ss24-nvy"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.color.id").value(COLOR_ID.toString()))
        .andExpect(jsonPath("$.data.matchedExternalCode").value("SS24-NVY"))
        .andExpect(jsonPath("$.data.matchedExternalName").value("Old navy"))
        .andExpect(jsonPath("$.data.matchedIsPrimary").value(false))
        .andExpect(jsonPath("$.data.primaryExternalCode").value("SS26-NVY-07"))
        .andExpect(jsonPath("$.data.colorPartnerRefId").value(REF_ID.toString()));
  }

  @Test
  @WithMockUser
  void writePermissionAllowsCreationAndDuplicateCodeIsConflict() throws Exception {
    allow("write");
    when(colorPartnerRefService.create(eq(COLOR_ID), any(CreateColorPartnerRefRequest.class)))
        .thenReturn(ref());

    mockMvc
        .perform(
            post("/api/v1/production/colors/{colorId}/partner-refs", COLOR_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.codes[0].externalCode").value("SS26-NVY-07"))
        .andExpect(jsonPath("$.data.codes[0].primary").value(true));

    when(colorPartnerRefService.create(eq(COLOR_ID), any(CreateColorPartnerRefRequest.class)))
        .thenThrow(ColorPartnerRefDomainException.duplicateCode("SS26-NVY-07"));

    mockMvc
        .perform(
            post("/api/v1/production/colors/{colorId}/partner-refs", COLOR_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRODUCTION_COLOR_PARTNER_CODE_DUPLICATE"));
  }

  @Test
  @WithMockUser
  void malformedCommandsUseTheCanonical422ValidationResponse() throws Exception {
    allow("write");

    mockMvc
        .perform(
            post("/api/v1/production/colors/{colorId}/partner-refs", COLOR_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"partnerId":null,"role":null,"deltaETolerance":0,
                     "initialPrimaryCode":{"externalCode":"","externalName":" padded "}}
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @WithMockUser
  void tolerancePrecisionAndScaleViolationsAreRejectedBeforePersistence() throws Exception {
    allow("write");

    mockMvc
        .perform(
            post("/api/v1/production/colors/{colorId}/partner-refs", COLOR_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"partnerId":"%s","role":"CUSTOMER","deltaETolerance":100,
                     "initialPrimaryCode":{"externalCode":"SS26-NVY-07"}}
                    """
                        .formatted(PARTNER_ID)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    mockMvc
        .perform(
            post("/api/v1/production/colors/{colorId}/partner-refs", COLOR_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"partnerId":"%s","role":"CUSTOMER","deltaETolerance":1.234,
                     "initialPrimaryCode":{"externalCode":"SS26-NVY-07"}}
                    """
                        .formatted(PARTNER_ID)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @WithMockUser
  void forwardWithoutAnActivePrimaryIsNotFound() throws Exception {
    allow("read");
    when(colorPartnerRefQueryService.forward(COLOR_ID, PARTNER_ID, PartnerRole.SUPPLIER))
        .thenThrow(new NotFoundException("No active primary partner color code found"));

    mockMvc
        .perform(
            get("/api/v1/production/color-partner-refs/forward")
                .param("colorId", COLOR_ID.toString())
                .param("partnerId", PARTNER_ID.toString())
                .param("role", "SUPPLIER"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @WithMockUser
  void staleMutationIsMappedToConflict() throws Exception {
    allow("write");
    when(colorPartnerRefService.create(eq(COLOR_ID), any(CreateColorPartnerRefRequest.class)))
        .thenThrow(new ObjectOptimisticLockingFailureException(ColorPartnerRef.class, REF_ID));

    mockMvc
        .perform(
            post("/api/v1/production/colors/{colorId}/partner-refs", COLOR_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK"));
  }

  private void allow(String action) {
    when(authEvaluator.can(any(Authentication.class), eq("colors"), eq(action))).thenReturn(true);
  }

  private ColorPartnerRef ref() {
    ColorPartnerRef ref =
        ColorPartnerRef.create(
            TENANT_ID, COLOR_ID, PARTNER_ID, PartnerRole.CUSTOMER, null, "SS26-NVY-07", "Navy");
    ref.setId(REF_ID);
    ref.getCodes().getFirst().setId(UUID.randomUUID());
    return ref;
  }

  private String validCreateBody() {
    return """
        {"partnerId":"%s","role":"CUSTOMER",
         "initialPrimaryCode":{"externalCode":"SS26-NVY-07","externalName":"Navy"}}
        """
        .formatted(PARTNER_ID);
  }
}
