package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.fiber.domain.FiberRequestStatus;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.dto.FiberRequestDto;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Platform staff review fibre requests from the platform's own tenant, under row-level security
 * (the real {@code fabric_app} role). Before the fix the queue read ran in the reviewer's tenant,
 * so another company's request was missing from the list, its detail was not found and no decision
 * could reach it.
 */
class FiberRequestPlatformQueueRealRolesIT extends AbstractFabricAppIntegrationTest {

  @Autowired private FiberRequestService service;

  private final List<UUID> createdTenants = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    JdbcTemplate owner = owner();
    for (UUID tenantId : createdTenants) {
      owner.update("DELETE FROM production.production_fiber_request WHERE tenant_id = ?", tenantId);
      owner.update("DELETE FROM common_tenant.common_tenant WHERE id = ?", tenantId);
    }
    createdTenants.clear();
  }

  @Test
  @DisplayName("platform staff see, open and decide another company's request")
  void reviewsAnotherTenantsRequest() {
    UUID platform = insertTenant("Platform");
    UUID company = insertTenant("Company");
    UUID pending = insertRequest(company, "DNM" + shortId(), "PENDING");
    UUID decided = insertRequest(company, "DNM" + shortId(), "REJECTED");

    // The request runs as the platform staff member's own tenant.
    UUID reviewer = UUID.randomUUID();
    TenantContext.restore(new TenantContext.TenantSnapshot(platform, "PLAT", reviewer, null));

    Page<FiberRequestDto> queue =
        service.listForPlatform(Optional.of(FiberRequestStatus.PENDING), PageRequest.of(0, 200));
    assertThat(queue.getContent()).extracting(FiberRequestDto::getId).contains(pending);
    assertThat(queue.getContent()).extracting(FiberRequestDto::getId).doesNotContain(decided);
    assertThat(queue.getContent())
        .filteredOn(request -> request.getId().equals(pending))
        .extracting(FiberRequestDto::getTenantName)
        .allMatch(name -> name.startsWith("Company "));

    Page<FiberRequestDto> all = service.listForPlatform(Optional.empty(), PageRequest.of(0, 200));
    assertThat(all.getContent()).extracting(FiberRequestDto::getId).contains(pending, decided);
    assertThat(all.getTotalElements()).isGreaterThanOrEqualTo(2);

    assertThat(service.getById(pending)).map(FiberRequestDto::getTenantId).contains(company);

    // A decision reaches the request: a decided one is found, locked and refused, not "not found".
    assertThatThrownBy(() -> service.reject(decided, "Already decided earlier", reviewer))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_REQUEST_INVALID_STATUS");
    assertThatThrownBy(() -> service.reject(UUID.randomUUID(), "No such request here", reviewer))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_REQUEST_NOT_FOUND");

    // The reviewer's tenant is restored afterwards.
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isEqualTo(platform);
  }

  private static JdbcTemplate owner() {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
  }

  private UUID insertTenant(String name) {
    UUID id = UUID.randomUUID();
    String suffix = shortId();
    owner()
        .update(
            "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email,"
                + " status, settings, demo_mode, is_active, created_at, updated_at, version)"
                + " VALUES (?, ?, ?, ?, 'REGULAR', 'admin@example.com', 'ACTIVE', '{}', false,"
                + " true, now(), now(), 0)",
            id,
            "FRQ-" + suffix,
            "frq-" + suffix.toLowerCase(Locale.ROOT),
            name + " " + suffix);
    createdTenants.add(id);
    return id;
  }

  private static UUID insertRequest(UUID tenantId, String isoCode, String status) {
    UUID id = UUID.randomUUID();
    owner()
        .update(
            "INSERT INTO production.production_fiber_request (id, tenant_id, uid, requested_by,"
                + " iso_code, fiber_name, fiber_type, status, is_active, created_at, updated_at,"
                + " version) VALUES (?, ?, ?, ?, ?, 'Deneme', 'NATURAL', ?, true, now(), now(), 0)",
            id,
            tenantId,
            UUID.randomUUID().toString(),
            UUID.randomUUID(),
            isoCode,
            status);
    return id;
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }
}
