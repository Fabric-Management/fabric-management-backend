package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.dto.FiberRequestDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FiberRequestSourceVariantIT extends FiberSourceIntegrationSupport {

  @Test
  void recycledPesApprovalReusesTenantIsoAndCategoryWithoutMintingReferenceRows() {
    UUID tenantId = insertTenant("pes-variant");
    UUID actorId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    UUID categoryId = insertCategory(tenantId, CATEGORY);
    UUID pesId = insertIso(tenantId, "PES", CATEGORY);
    insertPureFiber(tenantId, categoryId, pesId, "Virgin Polyester", MaterialSource.VIRGIN);
    long isoRowsBefore =
        queryOne(
            "SELECT count(*) FROM production.prod_fiber_iso_code "
                + "WHERE tenant_id = ? AND upper(iso_code) = 'PES'",
            Long.class,
            tenantId);

    useTenant(tenantId, actorId);
    FiberRequestDto submitted =
        fiberRequestService.submit(
            request("PES", "Recycled Polyester", CATEGORY, MaterialSource.RECYCLED),
            tenantId,
            actorId);
    FiberRequestDto approved = fiberRequestService.approve(submitted.getId(), reviewerId);

    assertThat(approved.getMaterialSource()).isEqualTo(MaterialSource.RECYCLED);
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_fiber_iso_code "
                    + "WHERE tenant_id = ? AND upper(iso_code) = 'PES'",
                Long.class,
                tenantId))
        .isEqualTo(isoRowsBefore);
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_fiber "
                    + "WHERE tenant_id = ? AND fiber_iso_code_id = ?",
                Long.class,
                tenantId,
                pesId))
        .isEqualTo(2L);
    assertThat(
            queryOne(
                "SELECT count(DISTINCT material_source) FROM production.prod_fiber "
                    + "WHERE tenant_id = ? AND fiber_iso_code_id = ?",
                Long.class,
                tenantId,
                pesId))
        .isEqualTo(2L);
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_fiber f "
                    + "JOIN production.prod_fiber_iso_code i ON i.id = f.fiber_iso_code_id "
                    + "JOIN production.prod_fiber_category c ON c.id = f.fiber_category_id "
                    + "WHERE f.tenant_id = ? AND i.tenant_id = ? AND c.tenant_id = ?",
                Long.class,
                tenantId,
                tenantId,
                tenantId))
        .isEqualTo(2L);
    assertThat(
            queryOne(
                "SELECT fiber_name FROM production.prod_fiber "
                    + "WHERE tenant_id = ? AND material_source = 'RECYCLED'",
                String.class,
                tenantId))
        .isEqualTo("Recycled Polyester");
  }

  @Test
  void existingCodeRequiresSourceAndKeepsItsCategoryClassification() {
    UUID tenantId = insertTenant("existing-matrix");
    UUID actorId = UUID.randomUUID();
    insertCategory(tenantId, CATEGORY);
    insertCategory(tenantId, "NATURAL_PLANT");
    insertIso(tenantId, "PES", CATEGORY);
    useTenant(tenantId, actorId);

    assertThatThrownBy(
            () ->
                fiberRequestService.submit(
                    request("PES", "Undifferentiated PES", CATEGORY, null), tenantId, actorId))
        .isInstanceOf(FiberDomainException.class)
        .satisfies(
            failure -> {
              FiberDomainException exception = (FiberDomainException) failure;
              assertThat(exception.getErrorCode())
                  .isEqualTo("FIBER_REQUEST_MATERIAL_SOURCE_REQUIRED");
              assertThat(exception.getHttpStatus()).isEqualTo(400);
            });
    assertThatThrownBy(
            () ->
                fiberRequestService.submit(
                    request("PES", "Wrong Type", "NATURAL_PLANT", MaterialSource.RECYCLED),
                    tenantId,
                    actorId))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_REQUEST_FIBER_TYPE_MISMATCH");
  }

  @Test
  void duplicateKeyIsNullSafeForDeclaredAndUndeclaredRequests() {
    UUID tenantId = insertTenant("duplicate-matrix");
    UUID actorId = UUID.randomUUID();
    insertCategory(tenantId, CATEGORY);
    useTenant(tenantId, actorId);
    String declaredCode = randomCode();
    String undeclaredCode = randomCode();

    fiberRequestService.submit(
        request(declaredCode, "Declared One", CATEGORY, MaterialSource.RECYCLED),
        tenantId,
        actorId);
    fiberRequestService.submit(
        request(undeclaredCode, "Undeclared One", CATEGORY, null), tenantId, actorId);

    assertDuplicate(
        tenantId,
        actorId,
        request(declaredCode, "Declared Two", CATEGORY, MaterialSource.RECYCLED));
    assertDuplicate(tenantId, actorId, request(undeclaredCode, "Undeclared Two", CATEGORY, null));
  }

  private void assertDuplicate(
      UUID tenantId,
      UUID actorId,
      com.fabricmanagement.product.fiber.dto.CreateFiberRequestRequest request) {
    assertThatThrownBy(() -> fiberRequestService.submit(request, tenantId, actorId))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_REQUEST_DUPLICATE_PENDING");
  }

  private String randomCode() {
    return "N" + UUID.randomUUID().toString().substring(0, 7).toUpperCase();
  }
}
