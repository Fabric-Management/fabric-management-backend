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

/**
 * FIBER-CATALOG-1 §7 (A05, A06): database invariants of blends. A blend has no ISO code, belongs to
 * the shared MIXED_BLEND category and never carries one material source.
 */
class FiberBlendVariantIT extends FiberSourceIntegrationSupport {

  @Test
  void blendsCarryNoIsoAndTheDatabaseRejectsIsoSourceAndForeignCategoryOnABlend() {
    UUID tenantId = insertTenant("blend-index");
    UUID cotton = canonicalFiberId("CO");
    UUID polyester = canonicalFiberId("PES");
    UUID firstBlend =
        insertBlend(tenantId, "60/40 CO/PES", composition(cotton, "60", polyester, "40"));
    insertBlend(tenantId, "70/30 CO/PES", composition(cotton, "70", polyester, "30"));

    assertThat(
            count(
                "SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ? "
                    + "AND fiber_iso_code_id IS NULL AND composition <> '{}'::jsonb",
                tenantId))
        .isEqualTo(2L);
    assertThatThrownBy(
            () ->
                update(
                    "UPDATE production.prod_fiber SET material_source = 'RECYCLED' WHERE id = ?",
                    firstBlend))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("chk_fiber_material_source_pure_only");
    assertThatThrownBy(
            () ->
                update(
                    "UPDATE production.prod_fiber SET fiber_iso_code_id = ? WHERE id = ?",
                    sharedIsoId("CO"),
                    firstBlend))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("chk_fiber_iso_matches_kind");
    assertThatThrownBy(
            () ->
                update(
                    "UPDATE production.prod_fiber SET fiber_category_id = ? WHERE id = ?",
                    sharedCategoryId("NATURAL_PLANT"),
                    firstBlend))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("chk_fiber_blend_mixed_category");
    assertThatThrownBy(
            () ->
                insertBlend(
                    tenantId,
                    "Same 60/40, other scale",
                    composition(polyester, "40.00", cotton, "60.0")))
        .as("the database backstop treats 60 and 60.0 as the same composition")
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_fiber_active_blend_composition");
  }

  @Test
  void serviceRejectsMaterialSourceAndIsoOnABlendBeforeCreatingAnything() {
    UUID tenantId = insertTenant("blend-service");
    useTenant(tenantId, UUID.randomUUID());
    Map<UUID, BigDecimal> composition =
        Map.of(
            canonicalFiberId("CO"), new BigDecimal("60.00"),
            canonicalFiberId("PES"), new BigDecimal("40.00"));

    assertThatThrownBy(
            () ->
                fiberService.createFiber(
                    CreateFiberRequest.builder()
                        .unit("KG")
                        .fiberName("Invalid sourced blend")
                        .materialSource(MaterialSource.RECYCLED)
                        .composition(composition)
                        .build()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_BLEND_MATERIAL_SOURCE_FORBIDDEN");
    assertThatThrownBy(
            () ->
                fiberService.createFiber(
                    CreateFiberRequest.builder()
                        .unit("KG")
                        .fiberIsoCodeId(sharedIsoId("CO"))
                        .composition(composition)
                        .build()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_BLEND_ISO_FORBIDDEN");
    assertThat(count("SELECT count(*) FROM production.prod_product WHERE tenant_id = ?", tenantId))
        .isZero();
  }

  private String composition(UUID firstId, String first, UUID secondId, String second) {
    return "{\"" + firstId + "\":" + first + ",\"" + secondId + "\":" + second + "}";
  }
}
