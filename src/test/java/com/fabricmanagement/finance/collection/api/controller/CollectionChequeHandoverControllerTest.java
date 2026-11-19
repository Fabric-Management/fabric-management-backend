package com.fabricmanagement.finance.collection.api.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.finance.collection.app.CollectionChequeHandoverQueryService;
import com.fabricmanagement.finance.collection.app.CollectionChequeHandoverService;
import com.fabricmanagement.finance.collection.app.CollectionChequePreviewService;
import com.fabricmanagement.platform.auth.app.JwtService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CollectionChequeHandoverController.class)
@EnableMethodSecurity
@ActiveProfiles("test")
class CollectionChequeHandoverControllerTest {
  private static final String PATH = "/api/v1/finance/collections/cheque-handovers/preview";

  @Autowired MockMvc mvc;
  @MockitoBean CollectionChequePreviewService previews;
  @MockitoBean CollectionChequeHandoverService handovers;
  @MockitoBean CollectionChequeHandoverQueryService queries;
  @MockitoBean JwtService jwtService;
  @MockitoBean TenantQueryPort tenantQueryPort;

  @MockitoBean(name = "auth")
  SpELPermissionEvaluator auth;

  @Test
  @WithMockUser
  void aFinanceReadGrantAloneDoesNotPermitPreview() throws Exception {
    when(auth.can(any(Authentication.class), eq("finance"), eq("read"))).thenReturn(true);
    mvc.perform(
            post(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
            {"partnerId":"%s","rows":[{"rowKey":"northbridge-1","intent":"AUTO"}]}
            """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isForbidden());
    verify(auth).can(any(Authentication.class), eq("finance"), eq("write"));
    verifyNoInteractions(previews);
  }

  @Test
  @WithMockUser
  void aFinanceReadGrantAloneDoesNotPermitSaveOrTargetRefresh() throws Exception {
    when(auth.can(any(Authentication.class), eq("finance"), eq("read"))).thenReturn(true);
    var base = "/api/v1/finance/collections/cheque-handovers";
    mvc.perform(
            post(base)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveBody("1000")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(base + "/target-refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"obligationIds\":[\"" + UUID.randomUUID() + "\"]}"))
        .andExpect(status().isForbidden());
    verify(auth, times(2)).can(any(Authentication.class), eq("finance"), eq("write"));
    verifyNoInteractions(handovers);
  }

  @Test
  @WithMockUser
  void invalidSaveAmountUsesAnIndexedFieldPath() throws Exception {
    when(auth.can(any(Authentication.class), eq("finance"), eq("write"))).thenReturn(true);
    mvc.perform(
            post("/api/v1/finance/collections/cheque-handovers")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveBody("0")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("rows[1].amount")));
    verifyNoInteractions(handovers);
  }

  @Test
  @WithMockUser
  void aFinanceWriteGrantAloneDoesNotPermitHandoverReads() throws Exception {
    when(auth.can(any(Authentication.class), eq("finance"), eq("write"))).thenReturn(true);
    var base = "/api/v1/finance/collections/cheque-handovers";
    mvc.perform(get(base)).andExpect(status().isForbidden());
    mvc.perform(get(base + "/" + UUID.randomUUID())).andExpect(status().isForbidden());
    verify(auth, times(2)).can(any(Authentication.class), eq("finance"), eq("read"));
    verifyNoInteractions(queries);
  }

  @Test
  void handoverListBindsThePublishedScalarPagingParameters() throws Exception {
    UUID actor = UUID.randomUUID();
    UUID partner = UUID.randomUUID();
    var principal =
        new AuthenticatedUserContext(actor, "FINANCE_MANAGER", List.of(), null, UUID.randomUUID());
    var token = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    var paging = PageRequest.of(2, 10);
    when(auth.can(any(Authentication.class), eq("finance"), eq("read"))).thenReturn(true);
    when(queries.list(actor, partner, "chb-2026-000001", paging)).thenReturn(Page.empty(paging));
    mvc.perform(
            get("/api/v1/finance/collections/cheque-handovers")
                .with(authentication(token))
                .param("partnerId", partner.toString())
                .param("number", "chb-2026-000001")
                .param("page", "2")
                .param("size", "10"))
        .andExpect(status().isOk());
    verify(queries).list(actor, partner, "chb-2026-000001", paging);
  }

  private String saveBody(String secondAmount) {
    return """
        {"operationId":"%s","partnerId":"%s","receivedOn":"2026-10-20",
         "occurredAt":"2026-10-20T12:00:00Z","reason":"Customer handed over two cheques",
         "evidence":"Northbridge Cloth handover slip","obligationVersions":[],"rows":[
           {"rowKey":"northbridge-1","instrumentReference":"CH-001","currency":"GBP","amount":"3000",
             "dueOn":"2027-01-20","drawerKind":"OWN","applications":[]},
           {"rowKey":"northbridge-2","instrumentReference":"CH-002","currency":"GBP","amount":"%s",
             "dueOn":"2027-02-20","drawerKind":"OWN","applications":[]}]}
        """
        .formatted(UUID.randomUUID(), UUID.randomUUID(), secondAmount);
  }

  @Test
  @WithMockUser
  void invalidPreviewInputReportsTheRowFieldPath() throws Exception {
    when(auth.can(any(Authentication.class), eq("finance"), eq("write"))).thenReturn(true);
    mvc.perform(
            post(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
            {"partnerId":"%s","rows":[{"rowKey":"northbridge-1","intent":"AUTO"},
              {"rowKey":"northbridge-2","intent":"FIXED","currency":"invalid"}]}
            """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("rows[1].currency")));
    verifyNoInteractions(previews);
  }
}
