package com.fabricmanagement.production.quality.result.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRegistryRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class FiberTestCatalogueIT extends FiberQualityHttpIntegrationSupport {

  private static final String STANDARDS = TESTS + "/standards";
  private static final String LABORATORIES = TESTS + "/laboratories";

  @Autowired private TradingPartnerRepository partners;
  @Autowired private TradingPartnerRegistryRepository registries;

  @Test
  void technicianSearchesTheCataloguesAndRecordedDetailsSurviveChanges() throws Exception {
    String manager = token("Alice", "Mason", "QC_CATALOGUE_MANAGER", "manage");
    String technician = token("Ed", "Shaw", "QC_CATALOGUE_TECHNICIAN", "write");
    String reader = token("Clara", "Booth", "QC_CATALOGUE_READER", "read");
    UUID lot = createLot("AB-CAT-" + tenantId.toString().substring(0, 8), new BigDecimal("200"));
    UUID laboratory = createPartner("Alder Beck Fibre Laboratory");
    UUID logistics = createPartner("Alder Beck Logistics");
    TenantContext.clear();

    String standard =
        id(
            createStandard(manager, "AB-MC", "Fibre moisture", "2026")
                .andExpect(status().isCreated()));
    createStandard(manager, "AB-MC", "Fibre moisture", "2025").andExpect(status().isCreated());
    createStandard(manager, "ab-mc", "Duplicate method", "2026")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FIBER_TEST_STANDARD_DUPLICATE"));
    registerLaboratory(manager, laboratory).andExpect(status().isOk());
    createStandard(technician, "AB-LN", "Fibre length", "2026").andExpect(status().isForbidden());
    registerLaboratory(technician, logistics).andExpect(status().isForbidden());
    mvc.perform(authorised(get(LABORATORIES), reader)).andExpect(status().isForbidden());

    mvc.perform(
            authorised(get(STANDARDS), technician)
                .param("search", "moistURE")
                .param("size", "1")
                .param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[0].id").value(standard));
    mvc.perform(authorised(get(LABORATORIES), technician).param("search", "alder"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(laboratory.toString()));

    recordTest(technician, lot, Map.of("laboratoryId", laboratory, "testLab", "Alder Beck"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ILLEGAL_ARGUMENT"));
    recordTest(technician, lot, Map.of("testStandardId", standard, "testStandard", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ILLEGAL_ARGUMENT"));

    String result =
        id(
            recordTest(
                    technician,
                    lot,
                    Map.of(
                        "laboratoryId", laboratory,
                        "testStandardId", standard))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.laboratoryId").value(laboratory.toString()))
                .andExpect(jsonPath("$.data.testStandardId").value(standard))
                .andExpect(jsonPath("$.data.testLab").value("Alder Beck Fibre Laboratory"))
                .andExpect(jsonPath("$.data.testStandard").value("AB-MC"))
                .andExpect(jsonPath("$.data.testStandardName").value("Fibre moisture"))
                .andExpect(jsonPath("$.data.testStandardRevision").value("2026")));

    mvc.perform(
            withBody(
                patch(STANDARDS + "/{id}", standard),
                manager,
                Map.of("name", "Cotton fibre moisture")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Cotton fibre moisture"));
    mvc.perform(authorised(post(STANDARDS + "/{id}/deactivate", standard), manager))
        .andExpect(status().isNoContent());
    mvc.perform(authorised(post(LABORATORIES + "/{id}/deactivate", laboratory), manager))
        .andExpect(status().isNoContent());
    mvc.perform(authorised(get(LABORATORIES), technician))
        .andExpect(jsonPath("$.data.totalElements").value(0));
    mvc.perform(authorised(get(STANDARDS), technician).param("search", "AB-MC"))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].revision").value("2025"));

    recordTest(technician, lot, Map.of("testStandardId", standard))
        .andExpect(status().isNotFound());
    recordTest(technician, lot, Map.of("laboratoryId", laboratory))
        .andExpect(status().isNotFound());
    systemJdbc.update(
        "UPDATE common_company.common_trading_partner SET custom_name = ? WHERE id = ?",
        "Alder Beck Fibre Services",
        laboratory);
    registerLaboratory(manager, laboratory).andExpect(status().isOk());
    mvc.perform(authorised(get(LABORATORIES), technician))
        .andExpect(jsonPath("$.data.content[0].id").value(laboratory.toString()))
        .andExpect(jsonPath("$.data.content[0].name").value("Alder Beck Fibre Services"));
    systemJdbc.update(
        "UPDATE common_company.common_trading_partner SET status = 'SUSPENDED' WHERE id = ?",
        laboratory);
    mvc.perform(authorised(get(LABORATORIES), technician))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
    recordTest(technician, lot, Map.of("laboratoryId", laboratory))
        .andExpect(status().isNotFound());

    mvc.perform(authorised(get(TESTS + "/{id}", result), reader))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.testLab").value("Alder Beck Fibre Laboratory"))
        .andExpect(jsonPath("$.data.testStandardName").value("Fibre moisture"))
        .andExpect(jsonPath("$.data.testStandardRevision").value("2026"));
  }

  @Test
  void foreignOrUnavailableSelectionsAreHiddenAndLegacyTextIsStillReadable() throws Exception {
    String manager = token("Grace", "Reed", "QC_FOREIGN_CATALOGUE_MANAGER", "manage");
    String technician = token("Owen", "Heath", "QC_FOREIGN_CATALOGUE_TECHNICIAN", "write");
    String reader = token("Ellen", "Field", "QC_LEGACY_RESULT_READER", "read");
    UUID lot = createLot("AB-LEG-" + tenantId.toString().substring(0, 8), new BigDecimal("200"));
    UUID suspended = createPartner("Northmere Textile Testing");
    systemJdbc.update(
        "UPDATE common_company.common_trading_partner SET status = 'SUSPENDED' WHERE id = ?",
        suspended);
    UUID foreignTenant = insertTenant();
    useTenant(foreignTenant);
    UUID foreignPartner = createPartner("Fenwick Fibre Laboratory");
    systemJdbc.update(
        """
        INSERT INTO production.production_quality_fiber_test_laboratory
          (tenant_id, uid, trading_partner_id)
        VALUES (?, ?, ?)
        """,
        foreignTenant,
        "FF-LAB-" + foreignTenant,
        foreignPartner);
    UUID foreignStandard =
        systemJdbc.queryForObject(
            """
        INSERT INTO production.prod_fiber_test_standard (tenant_id, uid, code, name)
        VALUES (?, ?, 'FF-MC', 'Fenwick moisture') RETURNING id
        """,
            UUID.class,
            foreignTenant,
            "FF-MC-" + foreignTenant);
    TenantContext.clear();

    registerLaboratory(manager, foreignPartner).andExpect(status().isNotFound());
    registerLaboratory(manager, suspended).andExpect(status().isNotFound());
    recordTest(technician, lot, Map.of("laboratoryId", foreignPartner))
        .andExpect(status().isNotFound());
    recordTest(technician, lot, Map.of("testStandardId", foreignStandard))
        .andExpect(status().isNotFound());
    mvc.perform(authorised(get(STANDARDS), technician).param("search", "Fenwick"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
    mvc.perform(authorised(get(LABORATORIES), technician).param("search", "Fenwick"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));

    String legacyResult =
        id(
            recordTest(
                    technician,
                    lot,
                    Map.of("testLab", "Marlow Fibre Testing", "testStandard", "MFT-MOISTURE"))
                .andExpect(status().isCreated()));
    mvc.perform(authorised(get(TESTS + "/{id}", legacyResult), reader))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.laboratoryId").doesNotExist())
        .andExpect(jsonPath("$.data.testStandardId").doesNotExist())
        .andExpect(jsonPath("$.data.testLab").value("Marlow Fibre Testing"))
        .andExpect(jsonPath("$.data.testStandard").value("MFT-MOISTURE"));
  }

  private UUID createPartner(String name) {
    TradingPartnerRegistry registry = TradingPartnerRegistry.create(null, name, "GBR");
    registry.setUid("REG-" + UUID.randomUUID());
    return partners
        .saveAndFlush(
            TradingPartner.create(
                registries.saveAndFlush(registry), PartnerType.SERVICE_PROVIDER, null))
        .getId();
  }

  private ResultActions createStandard(String token, String code, String name, String revision)
      throws Exception {
    return mvc.perform(
        withBody(post(STANDARDS), token, Map.of("code", code, "name", name, "revision", revision)));
  }

  private ResultActions registerLaboratory(String token, UUID partnerId) throws Exception {
    return mvc.perform(withBody(post(LABORATORIES), token, Map.of("tradingPartnerId", partnerId)));
  }

  private ResultActions recordTest(String token, UUID batchId, Map<String, ?> metadata)
      throws Exception {
    var details = new LinkedHashMap<String, Object>();
    details.put("batchId", batchId);
    details.put("testDate", Instant.now().minusSeconds(60));
    details.put("moisturePercent", 5);
    details.putAll(metadata);
    return mvc.perform(withBody(post(TESTS), token, details));
  }

  private MockHttpServletRequestBuilder withBody(
      MockHttpServletRequestBuilder request, String token, Map<String, ?> body) throws Exception {
    return authorised(request, token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body));
  }

  private static MockHttpServletRequestBuilder authorised(
      MockHttpServletRequestBuilder request, String token) {
    return request.header("Authorization", "Bearer " + token);
  }

  private String id(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString())
        .path("data")
        .path("id")
        .asText();
  }
}
