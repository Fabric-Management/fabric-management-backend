package com.fabricmanagement.sales.ownership.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.sales.ownership.domain.OwnershipTriageCase;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OwnershipTriageQueryRepositoryIT extends AbstractIntegrationTest {

  private static final Instant MODE_EFFECTIVE_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant RELATIONSHIP_ESTABLISHED_AT = Instant.parse("2026-07-01T00:00:00Z");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private OwnershipTriageQueryRepository repository;

  private UUID tenantId;
  private UUID organizationId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    organizationId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO common_tenant.common_tenant
            (id, uid, slug, name, status, type)
        VALUES (?, ?, ?, 'Triage Query Test', 'ACTIVE', 'REGULAR')
        """,
        tenantId,
        "TRIAGE-" + tenantId,
        "triage-" + tenantId);
    jdbc.update(
        """
        INSERT INTO common_company.common_organization
            (id, tenant_id, uid, name, tax_id, organization_type)
        VALUES (?, ?, ?, 'Triage Org', ?, 'VERTICAL_MILL')
        """,
        organizationId,
        tenantId,
        "TRIAGE-ORG-" + organizationId,
        "TAX-" + organizationId);
    jdbc.update(
        """
        INSERT INTO sales.ownership_policy
            (id, tenant_id, uid, default_mode, mode_effective_at,
             assignment_ladder_enabled, triage_age_threshold_hours)
        VALUES (gen_random_uuid(), ?, gen_random_uuid()::VARCHAR, 'REQUIRED', ?, FALSE, 24)
        """,
        tenantId,
        Timestamp.from(MODE_EFFECTIVE_AT));
  }

  @Test
  void gapStartsAtTheLatestAnchorAndQuoteDeletionCannotChangeTheCaseKey() {
    UUID customerId = insertCustomer(RELATIONSHIP_ESTABLISHED_AT);
    UUID quoteId = insertQuote(customerId, Instant.parse("2026-07-02T00:00:00Z"));

    OwnershipTriageCase initial = onlyCase();

    assertThat(initial.gapStartedAt()).isEqualTo(RELATIONSHIP_ESTABLISHED_AT);
    assertThat(initial.unassignedOpenQuoteCount()).isEqualTo(1);

    jdbc.update("UPDATE sales.quote SET is_active = FALSE WHERE id = ?", quoteId);
    OwnershipTriageCase afterQuoteDeletion = onlyCase();

    assertThat(afterQuoteDeletion.gapStartedAt()).isEqualTo(initial.gapStartedAt());
    assertThat(afterQuoteDeletion.unassignedOpenQuoteCount()).isZero();
  }

  @Test
  void theLatestAssignmentClosureWinsAndCreatesANewCaseKey() {
    UUID customerId = insertCustomer(RELATIONSHIP_ESTABLISHED_AT);
    Instant closedAt = Instant.parse("2026-07-20T00:00:00Z");
    insertClosedAssignment(customerId, closedAt);

    OwnershipTriageCase triageCase = onlyCase();

    assertThat(triageCase.gapStartedAt()).isEqualTo(closedAt);
    jdbc.update(
        """
        INSERT INTO sales.ownership_triage_case_log
            (tenant_id, uid, customer_id, gap_started_at, notification_requested_at)
        VALUES (?, gen_random_uuid()::VARCHAR, ?, ?, ?)
        """,
        tenantId,
        customerId,
        Timestamp.from(RELATIONSHIP_ESTABLISHED_AT),
        Timestamp.from(RELATIONSHIP_ESTABLISHED_AT));
    jdbc.update(
        """
        INSERT INTO sales.ownership_triage_case_log
            (tenant_id, uid, customer_id, gap_started_at, notification_requested_at)
        VALUES (?, gen_random_uuid()::VARCHAR, ?, ?, ?)
        """,
        tenantId,
        customerId,
        Timestamp.from(closedAt),
        Timestamp.from(closedAt));

    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM sales.ownership_triage_case_log
                WHERE tenant_id = ? AND customer_id = ?
                """,
                Long.class,
                tenantId,
                customerId))
        .isEqualTo(2);
  }

  @Test
  void legacyCustomerWithoutRelationshipTimestampUsesModeEffectiveAt() {
    insertCustomer(null);

    assertThat(onlyCase().gapStartedAt()).isEqualTo(MODE_EFFECTIVE_AT);
  }

  @Test
  void anOpenAssignmentIsEligibleOnlyWhileItsRepresentativeIsActive() {
    UUID customerId = insertCustomer(RELATIONSHIP_ESTABLISHED_AT);
    UUID representativeId = insertRepresentative(true);
    insertOpenAssignment(customerId, representativeId);

    assertThat(repository.findAll(tenantId)).isEmpty();

    jdbc.update(
        "UPDATE common_user.common_user SET is_active = FALSE WHERE id = ?", representativeId);

    assertThat(onlyCase().customerId()).isEqualTo(customerId);
  }

  @Test
  void queryUsesGreatestInsteadOfACoalesceFallbackChain() {
    assertThat(OwnershipTriageQueryRepository.DERIVED_TRIAGE_CTE)
        .contains("GREATEST(")
        .doesNotContainIgnoringCase("COALESCE(");
  }

  private OwnershipTriageCase onlyCase() {
    List<OwnershipTriageCase> cases = repository.findAll(tenantId);
    assertThat(cases).hasSize(1);
    return cases.getFirst();
  }

  private UUID insertCustomer(Instant establishedAt) {
    UUID registryId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO common_company.trading_partner_registry
            (id, uid, official_name, country)
        VALUES (?, ?, 'Triage Customer', 'GBR')
        """,
        registryId,
        "TRIAGE-REG-" + registryId);
    jdbc.update(
        """
        INSERT INTO common_company.common_trading_partner
            (id, tenant_id, uid, registry_id, custom_name, partner_type, status,
             customer_relationship_established_at)
        VALUES (?, ?, ?, ?, 'Triage Customer', 'CUSTOMER', 'ACTIVE', ?)
        """,
        customerId,
        tenantId,
        "TRIAGE-CUSTOMER-" + customerId,
        registryId,
        establishedAt == null ? null : Timestamp.from(establishedAt));
    return customerId;
  }

  private UUID insertQuote(UUID customerId, Instant createdAt) {
    UUID quoteId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO sales.quote
            (id, tenant_id, uid, quote_number, customer_id, assigned_to_id,
             owner_resolution_reason, module_type, status, valid_until, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, NULL, 'TRIAGE_REQUIRED', 'FABRIC', 'DRAFT',
                CURRENT_DATE + 30, ?, ?)
        """,
        quoteId,
        tenantId,
        "TRIAGE-QUOTE-" + quoteId,
        "QT-" + quoteId,
        customerId,
        Timestamp.from(createdAt),
        Timestamp.from(createdAt));
    return quoteId;
  }

  private UUID insertRepresentative(boolean active) {
    UUID representativeId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO common_user.common_user
            (id, tenant_id, uid, first_name, last_name, organization_id, user_type, is_active)
        VALUES (?, ?, ?, 'Triage', 'Representative', ?, 'INTERNAL', ?)
        """,
        representativeId,
        tenantId,
        "TRIAGE-USER-" + representativeId,
        organizationId,
        active);
    return representativeId;
  }

  private void insertClosedAssignment(UUID customerId, Instant validTo) {
    UUID representativeId = insertRepresentative(true);
    jdbc.update(
        """
        INSERT INTO sales.customer_commercial_assignment
            (id, tenant_id, uid, customer_id, representative_id, valid_from, valid_to,
             source, decided_by_type, decided_by_system_code, closed_by_type,
             closed_by_system_code, closure_reason, policy_version)
        VALUES (
            gen_random_uuid(), ?, gen_random_uuid()::VARCHAR, ?, ?,
            ?, ?, 'MANUAL', 'SYSTEM', 'OWNERSHIP_TEST',
            'SYSTEM', 'OWNERSHIP_TEST', 'SUPERSEDED', 'OWNERSHIP_POLICY_V1'
        )
        """,
        tenantId,
        customerId,
        representativeId,
        Timestamp.from(validTo.minusSeconds(3_600)),
        Timestamp.from(validTo));
  }

  private void insertOpenAssignment(UUID customerId, UUID representativeId) {
    jdbc.update(
        """
        INSERT INTO sales.customer_commercial_assignment
            (id, tenant_id, uid, customer_id, representative_id, valid_from,
             source, decided_by_type, decided_by_system_code, policy_version)
        VALUES (
            gen_random_uuid(), ?, gen_random_uuid()::VARCHAR, ?, ?, ?,
            'MANUAL', 'SYSTEM', 'OWNERSHIP_TEST', 'OWNERSHIP_POLICY_V1'
        )
        """,
        tenantId,
        customerId,
        representativeId,
        Timestamp.from(RELATIONSHIP_ESTABLISHED_AT));
  }
}
