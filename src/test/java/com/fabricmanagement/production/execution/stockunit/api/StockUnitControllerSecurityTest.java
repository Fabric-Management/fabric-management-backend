package com.fabricmanagement.production.execution.stockunit.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitQueryService;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
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

@WebMvcTest(StockUnitController.class)
@EnableMethodSecurity
class StockUnitControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private StockUnitService stockUnitService;
  @MockBean private StockUnitQueryService stockUnitQueryService;
  @MockBean private com.fabricmanagement.platform.auth.app.JwtService jwtService;

  @MockBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockBean(name = "auth")
  private SpELPermissionEvaluator authEvaluator;

  @Test
  @WithMockUser
  void qcRelocationRequiresQualityWrite() throws Exception {
    UUID stockUnitId = UUID.randomUUID();
    when(authEvaluator.can(any(Authentication.class), eq("quality"), eq("write")))
        .thenReturn(false);

    mockMvc
        .perform(
            post("/api/v1/production/stock-units/{id}/qc-relocate", stockUnitId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetLocationId":"%s","reason":"Move to inspection"}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void qualityWriterCanRelocatePendingUnit() throws Exception {
    UUID stockUnitId = UUID.randomUUID();
    UUID targetLocationId = UUID.randomUUID();
    when(authEvaluator.can(any(Authentication.class), eq("quality"), eq("write"))).thenReturn(true);
    StockUnit unit = pendingUnit(stockUnitId, targetLocationId);
    when(stockUnitService.relocateForQuality(stockUnitId, targetLocationId, "Move to inspection"))
        .thenReturn(unit);

    mockMvc
        .perform(
            post("/api/v1/production/stock-units/{id}/qc-relocate", stockUnitId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetLocationId":"%s","reason":"Move to inspection"}
                    """
                        .formatted(targetLocationId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(stockUnitId.toString()))
        .andExpect(jsonPath("$.data.qualityDisposition").value("PENDING_INSPECTION"));
  }

  private StockUnit pendingUnit(UUID id, UUID locationId) {
    StockUnit unit =
        StockUnit.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ProductType.FABRIC,
            "ROLL-QC-SECURITY",
            null,
            PackageType.ROLL,
            BigDecimal.TEN,
            null,
            "KG",
            locationId,
            StockUnitSourceType.GOODS_RECEIPT,
            UUID.randomUUID(),
            QualityDisposition.PENDING_INSPECTION);
    unit.setId(id);
    return unit;
  }
}
