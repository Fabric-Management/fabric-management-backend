package com.fabricmanagement.production.quality.result.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class FiberTestBaleChoicesIT extends FiberQualityHttpIntegrationSupport {

  private static final BigDecimal BALE_WEIGHT = new BigDecimal("20");

  @Test
  void everyActiveBaleOfTheLotIsFoundBeyondTheFirstPage() throws Exception {
    String technician = token("Ellis", "Ford", "QC_TECHNICIAN", "write");
    String reader = token("Lucy", "Bell", "QC_READER", "read");
    String batchCode = "ABS-CO-" + tenantId.toString().substring(0, 8);
    UUID batchId = createLot(batchCode, new BigDecimal("500"));
    var codes =
        IntStream.rangeClosed(1, 24)
            .mapToObj(number -> String.format(Locale.ROOT, "%s-B%02d", batchCode, number))
            .toList();
    Map<String, UUID> baleIds = new HashMap<>();
    for (String code : codes.reversed()) {
      baleIds.put(code, createBale(batchId, code, BALE_WEIGHT));
    }
    String heldCode = codes.get(21);
    UUID heldBale = baleIds.get(heldCode);
    assertThat(stockUnits.hold(heldBale, "Packaging inspection").getStatus())
        .isEqualTo(StockUnitStatus.ON_HOLD);

    UUID inactive = createBale(batchId, batchCode + "-B25", BALE_WEIGHT);
    systemJdbc.update(
        "UPDATE production.stock_unit SET is_active = false, deleted_at = now()"
            + " WHERE tenant_id = ? AND id = ?",
        tenantId,
        inactive);
    UUID otherLot = createLot(batchCode + "-OTHER", BALE_WEIGHT);
    createBale(otherLot, batchCode + "-OTHER-B24", BALE_WEIGHT);

    UUID foreignTenant = insertTenant();
    useTenant(foreignTenant);
    UUID foreignLot = createLot(batchCode, BALE_WEIGHT);
    createBale(foreignLot, batchCode + "-B24", BALE_WEIGHT);
    TenantContext.clear();

    mvc.perform(
            get(TESTS + "/batch/{batchId}", batchId)
                .header("Authorization", "Bearer " + technician))
        .andExpect(status().isForbidden());
    mvc.perform(bales(batchId, reader)).andExpect(status().isForbidden());
    mvc.perform(bales(foreignLot, technician)).andExpect(status().isNotFound());
    mvc.perform(bales(batchId, technician).param("page", "first"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"));
    mvc.perform(bales(batchId, technician).param("size", "201"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));

    mvc.perform(bales(batchId, technician))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.totalElements").value(24))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(
            jsonPath(
                "$.data.content[*].code", contains(codes.subList(0, 20).toArray(String[]::new))));

    mvc.perform(bales(batchId, technician).param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.last").value(true))
        .andExpect(
            jsonPath(
                "$.data.content[*].code", contains(codes.subList(20, 24).toArray(String[]::new))));

    mvc.perform(bales(batchId, technician).param("search", " b22 "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(heldBale.toString()))
        .andExpect(jsonPath("$.data.content[0].code").value(heldCode));

    String lateCode = codes.getLast();
    var search =
        mvc.perform(bales(batchId, technician).param("search", "b24"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].code").value(lateCode))
            .andExpect(jsonPath("$.data.content[0].id").value(baleIds.get(lateCode).toString()))
            .andReturn()
            .getResponse();
    String selectedBale =
        json.readTree(search.getContentAsString()).at("/data/content/0/id").asText();

    mvc.perform(bales(batchId, technician).param("search", "%"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.data.content").isEmpty());

    mvc.perform(
            post(TESTS)
                .header("Authorization", "Bearer " + technician)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"batchId":"%s","stockUnitId":"%s","testDate":"%s","moisturePercent":5}
                    """
                        .formatted(batchId, selectedBale, Instant.now().minusSeconds(60))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.stockUnitId").value(selectedBale));
  }

  private static MockHttpServletRequestBuilder bales(UUID batchId, String token) {
    return get(TESTS + "/batch/{batchId}/bales", batchId)
        .header("Authorization", "Bearer " + token);
  }
}
