package com.fabricmanagement.sales.quote.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.JwtAuthenticationFilter;
import com.fabricmanagement.common.infrastructure.security.RestAuthenticationEntryPoint;
import com.fabricmanagement.common.infrastructure.security.SecurityConfig;
import com.fabricmanagement.common.infrastructure.web.LocalizationFilter;
import com.fabricmanagement.sales.quote.app.QuoteApprovalService;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.dto.PublicQuoteResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicQuoteController.class)
@ActiveProfiles("test")
@Import({
  SecurityConfig.class,
  JwtAuthenticationFilter.class,
  LocalizationFilter.class,
  RestAuthenticationEntryPoint.class
})
class PublicQuoteControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private QuoteApprovalService quoteApprovalService;
  @MockitoBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockitoBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @Test
  void tokenReadAndApprovalNeedNoAuthenticatedSalesUser() throws Exception {
    Quote quote = publicQuote();
    when(quoteApprovalService.getPublicQuoteByToken("public-token"))
        .thenReturn(PublicQuoteResponse.from(quote));
    when(quoteApprovalService.processCustomerApproval("public-token", null, null, "Approved"))
        .thenReturn(quote);

    mockMvc
        .perform(get("/api/v1/public/sales/quotes/by-token/{token}", "public-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(quote.getId().toString()));
    mockMvc
        .perform(
            post("/api/v1/public/sales/quotes/approve")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"public-token\",\"customerNote\":\"Approved\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(quote.getId().toString()));
  }

  private Quote publicQuote() {
    Quote quote = new Quote();
    quote.setId(UUID.randomUUID());
    quote.setQuoteNumber("QT-PUBLIC-1");
    quote.setModuleType("FABRIC");
    quote.setStatus(QuoteStatus.APPROVED);
    quote.setCurrency("GBP");
    quote.setValidUntil(LocalDate.now().plusDays(30));
    quote.setPaymentTerms("NET_30");
    return quote;
  }
}
