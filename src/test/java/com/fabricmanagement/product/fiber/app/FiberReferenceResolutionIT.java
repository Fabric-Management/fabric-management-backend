package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.dto.FiberRequestDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FiberReferenceResolutionIT extends FiberSourceIntegrationSupport {

  @Test
  void ownRowsWinWhenOwnAndTemplateRowsAreBothVisible() {
    UUID tenantId = insertTenant("own-reference");
    UUID categoryId = insertCategory(tenantId, CATEGORY);
    UUID isoId = insertIso(tenantId, "PES", CATEGORY);
    UUID actorId = UUID.randomUUID();
    useTenant(tenantId, actorId);

    FiberRequestDto submitted =
        fiberRequestService.submit(
            request("PES", "Tenant Recycled PES", CATEGORY, MaterialSource.RECYCLED),
            tenantId,
            actorId);
    fiberRequestService.approve(submitted.getId(), UUID.randomUUID());

    assertThat(
            queryOne(
                "SELECT fiber_iso_code_id FROM production.prod_fiber "
                    + "WHERE tenant_id = ? AND material_source = 'RECYCLED'",
                UUID.class,
                tenantId))
        .isEqualTo(isoId);
    assertThat(
            queryOne(
                "SELECT fiber_category_id FROM production.prod_fiber "
                    + "WHERE tenant_id = ? AND material_source = 'RECYCLED'",
                UUID.class,
                tenantId))
        .isEqualTo(categoryId);
  }

  @Test
  void templateOnlyIsoFailsThenCloneRepairAllowsExactlyOneVariant() {
    UUID tenantId = insertTenant("clone-repair");
    UUID actorId = UUID.randomUUID();
    insertCategory(tenantId, CATEGORY);
    UUID requestId =
        insertPendingRequest(
            tenantId, actorId, "PES", "Recycled Polyester", CATEGORY, MaterialSource.RECYCLED);
    useTenant(tenantId, actorId);

    assertThatThrownBy(() -> fiberRequestService.approve(requestId, UUID.randomUUID()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_TENANT_REFERENCE_DATA_MISSING");
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ?",
                Long.class,
                tenantId))
        .isZero();

    UUID tenantPesId = insertIso(tenantId, "PES", CATEGORY);
    fiberRequestService.approve(requestId, UUID.randomUUID());

    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_fiber "
                    + "WHERE tenant_id = ? AND fiber_iso_code_id = ? AND material_source = 'RECYCLED'",
                Long.class,
                tenantId,
                tenantPesId))
        .isEqualTo(1L);
  }

  @Test
  void genuinelyNewCodeCreatesTenantOwnedIso() {
    UUID tenantId = insertTenant("new-code");
    UUID actorId = UUID.randomUUID();
    insertCategory(tenantId, CATEGORY);
    String isoCode = "N" + UUID.randomUUID().toString().substring(0, 7).toUpperCase();
    useTenant(tenantId, actorId);

    FiberRequestDto submitted =
        fiberRequestService.submit(
            request(isoCode, "New Recycled Fiber", CATEGORY, MaterialSource.RECYCLED),
            tenantId,
            actorId);
    fiberRequestService.approve(submitted.getId(), UUID.randomUUID());

    assertThat(
            queryOne(
                "SELECT tenant_id FROM production.prod_fiber_iso_code WHERE upper(iso_code) = ?",
                UUID.class,
                isoCode))
        .isEqualTo(tenantId);
  }

  @Test
  void unknownCategoryFailsWithNamed404() {
    UUID tenantId = insertTenant("unknown-category");
    UUID actorId = UUID.randomUUID();
    String isoCode = "N" + UUID.randomUUID().toString().substring(0, 7).toUpperCase();
    useTenant(tenantId, actorId);

    assertThatThrownBy(
            () ->
                fiberRequestService.submit(
                    request(isoCode, "Unknown Category Fiber", "NO_SUCH_CATEGORY", null),
                    tenantId,
                    actorId))
        .isInstanceOf(FiberDomainException.class)
        .satisfies(
            failure -> {
              FiberDomainException exception = (FiberDomainException) failure;
              assertThat(exception.getErrorCode()).isEqualTo("FIBER_CATEGORY_NOT_FOUND");
              assertThat(exception.getHttpStatus()).isEqualTo(404);
            });
  }
}
