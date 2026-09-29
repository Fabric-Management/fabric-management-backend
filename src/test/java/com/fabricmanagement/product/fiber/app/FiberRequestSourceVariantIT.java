package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequestRequest;
import com.fabricmanagement.product.fiber.dto.FiberRequestDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FIBER-CATALOG-1 §6 (A09): source-variant requests against the shared catalogue. */
class FiberRequestSourceVariantIT extends FiberSourceIntegrationSupport {

  @Test
  void recycledPesApprovalCreatesOnePrivateVariantOnTheSharedIsoWithoutReferenceRows() {
    UUID tenantId = insertTenant("pes-variant");
    UUID actorId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    insertPrivateVariant(tenantId, "PES", CATEGORY, "Virgin Polyester", MaterialSource.VIRGIN);
    long sharedIsoRows =
        count("SELECT count(*) FROM production.prod_fiber_iso_code WHERE iso_code = 'PES'");

    useTenant(tenantId, actorId);
    FiberRequestDto submitted =
        fiberRequestService.submit(
            request("PES", "Recycled Polyester", CATEGORY, MaterialSource.RECYCLED),
            tenantId,
            actorId);
    FiberRequestDto approved = fiberRequestService.approve(submitted.getId(), reviewerId);

    assertThat(approved.getMaterialSource()).isEqualTo(MaterialSource.RECYCLED);
    assertThat(count("SELECT count(*) FROM production.prod_fiber_iso_code WHERE iso_code = 'PES'"))
        .isEqualTo(sharedIsoRows)
        .isEqualTo(1L);
    assertThat(
            count(
                "SELECT count(*) FROM production.prod_fiber "
                    + "WHERE tenant_id = ? AND fiber_iso_code_id = ?",
                tenantId,
                sharedIsoId("PES")))
        .isEqualTo(2L);
    assertThat(
            count(
                "SELECT count(*) FROM production.prod_fiber f "
                    + "JOIN production.prod_fiber_iso_code i ON i.id = f.fiber_iso_code_id "
                    + "JOIN production.prod_fiber_category c ON c.id = f.fiber_category_id "
                    + "WHERE f.tenant_id = ? AND i.tenant_id = ? AND c.tenant_id = ?",
                tenantId,
                FiberCatalog.OWNER_ID,
                FiberCatalog.OWNER_ID))
        .as("both variants reference the shared ISO and category rows")
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
  void sharedCodeWithoutSourceIsRedundantAndTypeMismatchIsRejected() {
    UUID tenantId = insertTenant("existing-matrix");
    UUID actorId = UUID.randomUUID();
    useTenant(tenantId, actorId);

    assertThatThrownBy(
            () ->
                fiberRequestService.submit(
                    request("PES", "Undifferentiated PES", CATEGORY, null), tenantId, actorId))
        .isInstanceOf(FiberDomainException.class)
        .satisfies(
            failure -> {
              FiberDomainException exception = (FiberDomainException) failure;
              assertThat(exception.getErrorCode()).isEqualTo("FIBER_REQUEST_DUPLICATE_CATALOG");
              assertThat(exception.getHttpStatus()).isEqualTo(409);
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
  void existingVariantAtApprovalIsANamedConflictAndLeavesTheRequestPending() {
    UUID tenantId = insertTenant("variant-exists");
    UUID actorId = UUID.randomUUID();
    UUID requestId =
        insertPendingRequest(
            tenantId, actorId, "CO", "Recycled Cotton", "NATURAL_PLANT", MaterialSource.RECYCLED);
    insertPrivateVariant(
        tenantId, "CO", "NATURAL_PLANT", "Recycled Cotton (existing)", MaterialSource.RECYCLED);
    useTenant(tenantId, actorId);

    assertThatThrownBy(() -> fiberRequestService.approve(requestId, UUID.randomUUID()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_REQUEST_VARIANT_EXISTS");
    assertThat(
            queryOne(
                "SELECT status FROM production.production_fiber_request WHERE id = ?",
                String.class,
                requestId))
        .isEqualTo("PENDING");
    assertThat(
            count(
                "SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ? "
                    + "AND material_source = 'RECYCLED'",
                tenantId))
        .isEqualTo(1L);
  }

  @Test
  void duplicateKeyIsNullSafeForDeclaredAndUndeclaredRequests() {
    UUID tenantId = insertTenant("duplicate-matrix");
    UUID actorId = UUID.randomUUID();
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

  private void assertDuplicate(UUID tenantId, UUID actorId, CreateFiberRequestRequest request) {
    assertThatThrownBy(() -> fiberRequestService.submit(request, tenantId, actorId))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_REQUEST_DUPLICATE_PENDING");
  }
}
