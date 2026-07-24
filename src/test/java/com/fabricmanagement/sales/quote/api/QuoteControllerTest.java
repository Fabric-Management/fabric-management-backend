package com.fabricmanagement.sales.quote.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.sales.quote.app.QuoteApprovalService;
import com.fabricmanagement.sales.quote.app.QuoteService;
import com.fabricmanagement.sales.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.sales.quote.mapper.QuoteMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuoteController.class)
@EnableMethodSecurity
class QuoteControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private QuoteService quoteService;
  @MockBean private QuoteApprovalService quoteApprovalService;
  @MockBean private QuoteMapper quoteMapper;
  @MockBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  @Test
  @WithMockUser
  void invalidFulfillmentModeReturnsBadRequestBeforeServiceInvocation() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("sales"), eq("write"))).thenReturn(true);
    UUID quoteId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/sales/quotes/{quoteId}/lines", quoteId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "productId": "11111111-1111-4111-8111-111111111111",
                      "requestedQty": 2,
                      "unit": "M",
                      "offeredPrice": 12.5,
                      "fulfillmentMode": "INVALID"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

    verify(quoteService, never()).addQuoteLine(eq(quoteId), any(AddQuoteLineRequest.class));
  }
}
