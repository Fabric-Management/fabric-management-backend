package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.dto.FiberRequestDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * FIBER-CATALOG-1 §6 (A09, A10): fibre requests resolve against the shared catalogue. This
 * supersedes FIBER-SRC-1 R3 (tenant-local ISO rows, clone repair, tenant-minted new codes).
 */
class FiberReferenceResolutionIT extends FiberSourceIntegrationSupport {

  @Test
  void sourceVariantApprovalReferencesSharedIsoAndCategoryWithoutAnyTenantCopy() {
    UUID tenantId = insertTenant("shared-reference");
    UUID actorId = UUID.randomUUID();
    useTenant(tenantId, actorId);

    FiberRequestDto submitted =
        fiberRequestService.submit(
            request("pes", "Tenant Recycled PES", CATEGORY, MaterialSource.RECYCLED),
            tenantId,
            actorId);
    fiberRequestService.approve(submitted.getId(), UUID.randomUUID());

    assertThat(submitted.getIsoCode()).isEqualTo("PES");
    assertThat(
            queryOne(
                "SELECT fiber_iso_code_id FROM production.prod_fiber "
                    + "WHERE tenant_id = ? AND material_source = 'RECYCLED'",
                UUID.class,
                tenantId))
        .isEqualTo(sharedIsoId("PES"));
    assertThat(
            queryOne(
                "SELECT fiber_category_id FROM production.prod_fiber "
                    + "WHERE tenant_id = ? AND material_source = 'RECYCLED'",
                UUID.class,
                tenantId))
        .isEqualTo(sharedCategoryId(CATEGORY));
    assertThat(
            count(
                "SELECT count(*) FROM production.prod_fiber_iso_code WHERE tenant_id <> ?",
                FiberCatalog.OWNER_ID))
        .as("no tenant ISO rows exist anywhere")
        .isZero();
    assertThat(
            queryOne(
                "SELECT material_source FROM production.prod_fiber WHERE id = ?",
                String.class,
                canonicalFiberId("PES")))
        .as("the canonical undeclared fibre is never mutated")
        .isNull();
  }

  @Test
  void unknownCodeStaysPendingUntilPublishedThenResolvesTheSameSharedIsoForEveryTenant() {
    UUID first = insertTenant("new-code-a");
    UUID second = insertTenant("new-code-b");
    UUID actor = UUID.randomUUID();
    UUID reviewer = UUID.randomUUID();
    String code = randomCode();

    useTenant(first, actor);
    FiberRequestDto undeclared =
        fiberRequestService.submit(request(code, "New Fiber", CATEGORY, null), first, actor);
    FiberRequestDto recycled =
        fiberRequestService.submit(
            request(code, "Recycled New Fiber", CATEGORY, MaterialSource.RECYCLED), first, actor);
    useTenant(second, actor);
    FiberRequestDto otherTenant =
        fiberRequestService.submit(
            request(code, "Other Tenant Virgin", CATEGORY, MaterialSource.VIRGIN), second, actor);

    for (UUID requestId : new UUID[] {undeclared.getId(), recycled.getId()}) {
      assertThatThrownBy(() -> fiberRequestService.approve(requestId, reviewer))
          .isInstanceOf(FiberDomainException.class)
          .satisfies(
              failure -> {
                FiberDomainException exception = (FiberDomainException) failure;
                assertThat(exception.getErrorCode())
                    .isEqualTo("FIBER_CATALOG_PUBLICATION_REQUIRED");
                assertThat(exception.getHttpStatus()).isEqualTo(409);
              });
      assertThat(status(requestId)).isEqualTo("PENDING");
    }
    assertThat(
            count("SELECT count(*) FROM production.prod_fiber_iso_code WHERE iso_code = ?", code))
        .as("approval never mints an ISO code")
        .isZero();
    assertThat(count("SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ?", first))
        .isZero();

    String published = code;
    publishRequestedCode(published);

    fiberRequestService.approve(undeclared.getId(), reviewer);
    fiberRequestService.approve(recycled.getId(), reviewer);
    fiberRequestService.approve(otherTenant.getId(), reviewer);

    UUID sharedIso = sharedIsoId(published);
    assertThat(status(undeclared.getId())).isEqualTo("APPROVED");
    assertThat(
            count(
                "SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ? "
                    + "AND material_source IS NULL",
                first))
        .as("an undeclared request is fulfilled by the shared canonical fibre")
        .isZero();
    assertThat(
            queryOne(
                "SELECT fiber_iso_code_id FROM production.prod_fiber "
                    + "WHERE tenant_id = ? AND material_source = 'RECYCLED'",
                UUID.class,
                first))
        .isEqualTo(sharedIso);
    assertThat(
            queryOne(
                "SELECT fiber_iso_code_id FROM production.prod_fiber "
                    + "WHERE tenant_id = ? AND material_source = 'VIRGIN'",
                UUID.class,
                second))
        .isEqualTo(sharedIso);
  }

  @Test
  void incompletePublicationAndTypeMismatchAreNamedConflicts() {
    UUID tenantId = insertTenant("incomplete");
    UUID actor = UUID.randomUUID();
    String isoOnly = publishIsoRowOnly(CATEGORY);
    UUID pending =
        insertPendingRequest(
            tenantId, actor, isoOnly, "Incomplete", CATEGORY, MaterialSource.VIRGIN);
    UUID mismatched =
        insertPendingRequest(
            tenantId, actor, "CO", "Cotton as polymer", CATEGORY, MaterialSource.RECYCLED);
    useTenant(tenantId, actor);

    assertThatThrownBy(() -> fiberRequestService.approve(pending, UUID.randomUUID()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_CATALOG_PUBLICATION_INCOMPLETE");
    assertThatThrownBy(() -> fiberRequestService.approve(mismatched, UUID.randomUUID()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_REQUEST_FIBER_TYPE_MISMATCH");
    assertThat(status(pending)).isEqualTo("PENDING");
    assertThat(status(mismatched)).isEqualTo("PENDING");

    update(
        "UPDATE production.prod_fiber_iso_code SET is_active = FALSE WHERE iso_code = ?", isoOnly);
    assertThatThrownBy(() -> fiberRequestService.approve(pending, UUID.randomUUID()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_CATALOG_CODE_INACTIVE");
    assertThat(count("SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ?", tenantId))
        .isZero();
  }

  @Test
  void unknownCategoryFailsWithNamed404() {
    UUID tenantId = insertTenant("unknown-category");
    UUID actorId = UUID.randomUUID();
    String isoCode = randomCode();
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

  private String status(UUID requestId) {
    return queryOne(
        "SELECT status FROM production.production_fiber_request WHERE id = ?",
        String.class,
        requestId);
  }

  /** Publishes exactly the requested code (ISO row + canonical fibre) as a catalogue release. */
  private void publishRequestedCode(String isoCode) {
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.prod_fiber_iso_code "
                  + "(id, tenant_id, uid, iso_code, fiber_name, fiber_type, is_official_iso, "
                  + "is_active) VALUES (?, ?, ?, ?, ?, ?, FALSE, TRUE)",
              UUID.randomUUID(),
              FiberCatalog.OWNER_ID,
              uid("FISO"),
              isoCode,
              isoCode + " Test Fiber",
              CATEGORY);
          return null;
        });
    completePublication(isoCode);
  }
}
