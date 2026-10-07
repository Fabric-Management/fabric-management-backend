package com.fabricmanagement.sales.orderintake.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.sales.orderintake.app.OrderIntakeStockPreviewService;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult.EvaluationStatus;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewDto;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewDto.Quantity;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * STOCK-PREVIEW-1 A4: permission and request validation of the stock preview endpoint. Bean
 * validation failures are 422, as GlobalExceptionHandler maps them across the API.
 */
@WebMvcTest(controllers = OrderIntakeStockPreviewController.class)
@EnableMethodSecurity
class OrderIntakeStockPreviewControllerTest {

  private static final String PATH = "/api/v1/sales/order-intake/stock-preview";
  private static final UUID PRODUCT_ID = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OrderIntakeStockPreviewService previewService;
  @MockitoBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockitoBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockitoBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  void theEndpointNeedsSalesRead() throws NoSuchMethodException {
    assertThat(
            OrderIntakeStockPreviewController.class
                .getMethod("preview", OrderIntakeStockPreviewRequest.class)
                .getAnnotation(PreAuthorize.class)
                .value())
        .isEqualTo("@auth.can(authentication, 'sales', 'read')");
  }

  @Test
  @WithMockUser
  void withoutSalesReadItIsForbidden() throws Exception {
    when(authEvaluator.can(any(Authentication.class), eq("sales"), eq("read"))).thenReturn(false);

    mockMvc
        .perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
        .andExpect(status().isForbidden());

    verify(previewService, never()).preview(any(), any());
  }

  @Test
  @WithMockUser
  void aMissingRequestedQuantityIsRefused() throws Exception {
    allowSalesRead();

    mockMvc
        .perform(
            post(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productId":"%s","unit":"M","singleLotRequired":false}
                    """
                        .formatted(PRODUCT_ID)))
        .andExpect(status().isUnprocessableEntity());

    verify(previewService, never()).preview(any(), any());
  }

  @Test
  @WithMockUser
  void aMissingUnitIsRefused() throws Exception {
    allowSalesRead();

    mockMvc
        .perform(
            post(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productId":"%s","requestedQty":100,"singleLotRequired":false}
                    """
                        .formatted(PRODUCT_ID)))
        .andExpect(status().isUnprocessableEntity());

    verify(previewService, never()).preview(any(), any());
  }

  @Test
  @WithMockUser
  void aToleranceAboveOneHundredPercentIsRefused() throws Exception {
    allowSalesRead();

    mockMvc
        .perform(
            post(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productId":"%s","requestedQty":100,"unit":"M","singleLotRequired":false,
                     "toleranceUpPct":101}
                    """
                        .formatted(PRODUCT_ID)))
        .andExpect(status().isUnprocessableEntity());

    verify(previewService, never()).preview(any(), any());
  }

  @Test
  void theAuthenticatedActorGetsThePreview() throws Exception {
    UUID actor = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    allowSalesRead();
    when(previewService.preview(any(OrderIntakeStockPreviewRequest.class), eq(actor)))
        .thenReturn(emptyPreview());
    UsernamePasswordAuthenticationToken user =
        new UsernamePasswordAuthenticationToken("seller", null, List.of());
    user.setDetails(
        new AuthenticatedUserContext(actor, "SALES", List.of(), null, tenantId, false, null));
    TenantContext.setCurrentTenantId(tenantId);

    mockMvc
        .perform(
            post(PATH)
                .with(authentication(user))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totals.freeComplete").value(true))
        .andExpect(jsonPath("$.data.totals.notCoveredByReadyStock.canonical").value(100))
        .andExpect(jsonPath("$.data.lots").isEmpty());

    verify(previewService).preview(any(OrderIntakeStockPreviewRequest.class), eq(actor));
  }

  private void allowSalesRead() {
    when(authEvaluator.can(any(Authentication.class), eq("sales"), eq("read"))).thenReturn(true);
  }

  private static String body() {
    return """
        {"productId":"%s","requestedQty":100,"unit":"M","singleLotRequired":false,
         "toleranceUpPct":5,"toleranceDownPct":5}
        """
        .formatted(PRODUCT_ID);
  }

  private static OrderIntakeStockPreviewDto emptyPreview() {
    Quantity zero = new Quantity(BigDecimal.ZERO, BigDecimal.ZERO);
    return new OrderIntakeStockPreviewDto(
        "M",
        "M",
        new OrderIntakeStockPreviewDto.Evaluation(
            EvaluationStatus.NO_ELIGIBLE_STOCK,
            new BigDecimal("100"),
            "M",
            "M",
            List.of(),
            0,
            0,
            0,
            List.of(),
            false,
            0),
        List.of(),
        List.of(),
        new OrderIntakeStockPreviewDto.Totals(
            zero,
            zero,
            zero,
            zero,
            new Quantity(new BigDecimal("100"), new BigDecimal("100")),
            true,
            List.of()),
        0);
  }
}
