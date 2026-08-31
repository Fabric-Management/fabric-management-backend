package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequest;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class FiberBlendVariantIT extends FiberSourceIntegrationSupport {

  @Test
  void twoBlendsMaySharePrimaryIsoAndDatabaseRejectsSourceOnBlend() {
    UUID tenantId = insertTenant("blend-index");
    UUID categoryId = insertCategory(tenantId, "MIXED_BLEND");
    UUID primaryIsoId = insertIso(tenantId, "PES", CATEGORY);
    UUID pesComponent = UUID.randomUUID();
    UUID cottonComponent = UUID.randomUUID();
    UUID viscoseComponent = UUID.randomUUID();
    UUID firstBlend =
        insertBlend(
            tenantId,
            categoryId,
            primaryIsoId,
            "60/40 PES/CO",
            composition(pesComponent, "60.00", cottonComponent, "40.00"));
    insertBlend(
        tenantId,
        categoryId,
        primaryIsoId,
        "70/30 PES/CV",
        composition(pesComponent, "70.00", viscoseComponent, "30.00"));

    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_fiber "
                    + "WHERE tenant_id = ? AND fiber_iso_code_id = ? "
                    + "AND composition <> '{}'::jsonb",
                Long.class,
                tenantId,
                primaryIsoId))
        .isEqualTo(2L);
    assertThatThrownBy(
            () ->
                update(
                    "UPDATE production.prod_fiber SET material_source = 'RECYCLED' WHERE id = ?",
                    firstBlend))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("chk_fiber_material_source_pure_only");
  }

  @Test
  void serviceRejectsMaterialSourceBeforeTryingToCreateBlend() {
    UUID tenantId = insertTenant("blend-service");
    useTenant(tenantId, UUID.randomUUID());
    CreateFiberRequest request =
        CreateFiberRequest.builder()
            .unit("KG")
            .fiberName("Invalid sourced blend")
            .materialSource(MaterialSource.RECYCLED)
            .composition(
                Map.of(
                    UUID.randomUUID(), new BigDecimal("60.00"),
                    UUID.randomUUID(), new BigDecimal("40.00")))
            .build();

    assertThatThrownBy(() -> fiberService.createFiber(request))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_BLEND_MATERIAL_SOURCE_FORBIDDEN");
  }

  private String composition(UUID firstId, String first, UUID secondId, String second) {
    return "{\"" + firstId + "\":" + first + ",\"" + secondId + "\":" + second + "}";
  }
}
