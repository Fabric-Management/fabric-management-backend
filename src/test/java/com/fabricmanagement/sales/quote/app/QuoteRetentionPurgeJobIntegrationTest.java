package com.fabricmanagement.sales.quote.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class QuoteRetentionPurgeJobIntegrationTest extends AbstractIntegrationTest {

  private static final int ABANDONED_DRAFT_DAYS = 90;
  private static final int DEAD_TOKEN_DAYS = 365;

  @Autowired private QuoteRetentionPurgeJob job;
  @Autowired private JdbcTemplate jdbc;

  private final UUID tenantA = UUID.randomUUID();
  private final UUID tenantB = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    insertTenant(tenantA, "a");
    insertTenant(tenantB, "b");
    ReflectionTestUtils.setField(job, "enabled", true);
    ReflectionTestUtils.setField(job, "abandonedDraftDays", ABANDONED_DRAFT_DAYS);
    ReflectionTestUtils.setField(job, "deadTokenDays", DEAD_TOKEN_DAYS);
  }

  @AfterEach
  void tearDown() {
    ReflectionTestUtils.setField(job, "enabled", false);
    deleteTenantFixtures(tenantA);
    deleteTenantFixtures(tenantB);
  }

  /**
   * Testcontainers connects as a PostgreSQL superuser and therefore bypasses RLS. This test proves
   * the explicit tenant iteration and retention predicates, not production-role RLS enforcement. A
   * real-environment smoke check with fabric_system/fabric_app roles remains required.
   */
  @Test
  void purgesEligibleRowsAcrossTwoTenantsAndPreservesBusinessEvidence() {
    Instant now = Instant.now();
    Instant staleDraftTime = now.minus(ABANDONED_DRAFT_DAYS + 10L, ChronoUnit.DAYS);
    Instant staleTokenTime = now.minus(DEAD_TOKEN_DAYS + 10L, ChronoUnit.DAYS);
    Instant recentTime = now.minus(5, ChronoUnit.DAYS);

    UUID staleEmptyDraftA = insertQuote(tenantA, "DRAFT", staleDraftTime, staleDraftTime);
    UUID staleDraftWithLine = insertQuote(tenantA, "DRAFT", staleDraftTime, staleDraftTime);
    insertQuoteLine(tenantA, staleDraftWithLine);
    UUID recentlyTouchedDraft = insertQuote(tenantA, "DRAFT", staleDraftTime, recentTime);
    UUID staleApprovedQuote = insertQuote(tenantA, "APPROVED", staleDraftTime, staleDraftTime);

    UUID usedToken =
        insertToken(tenantA, staleApprovedQuote, "USED", staleTokenTime, staleTokenTime);
    UUID staleExpiredTokenA =
        insertToken(tenantA, staleApprovedQuote, "EXPIRED", staleTokenTime, null);
    UUID staleRevokedToken =
        insertToken(tenantA, staleApprovedQuote, "REVOKED", staleTokenTime, null);
    UUID recentExpiredToken = insertToken(tenantA, staleApprovedQuote, "EXPIRED", recentTime, null);

    UUID staleEmptyDraftB = insertQuote(tenantB, "DRAFT", staleDraftTime, staleDraftTime);
    UUID approvedQuoteB = insertQuote(tenantB, "APPROVED", staleDraftTime, staleDraftTime);
    UUID staleExpiredTokenB = insertToken(tenantB, approvedQuoteB, "EXPIRED", staleTokenTime, null);

    job.purgeQuotesAndTokens();

    assertThat(rowExists("sales.quote", staleEmptyDraftA)).isFalse();
    assertThat(rowExists("sales.quote", staleEmptyDraftB)).isFalse();
    assertThat(rowExists("sales.quote", staleDraftWithLine)).isTrue();
    assertThat(rowExists("sales.quote", recentlyTouchedDraft)).isTrue();
    assertThat(rowExists("sales.quote", staleApprovedQuote)).isTrue();

    assertThat(rowExists("sales.quote_approval_token", usedToken)).isTrue();
    assertThat(rowExists("sales.quote_approval_token", staleExpiredTokenA)).isFalse();
    assertThat(rowExists("sales.quote_approval_token", staleRevokedToken)).isFalse();
    assertThat(rowExists("sales.quote_approval_token", recentExpiredToken)).isTrue();
    assertThat(rowExists("sales.quote_approval_token", staleExpiredTokenB)).isFalse();
  }

  private void insertTenant(UUID tenantId, String suffix) {
    String unique = tenantId.toString();
    jdbc.update(
        """
        INSERT INTO common_tenant.common_tenant
          (id, uid, slug, name, type, billing_email, status, settings, is_active,
           created_at, updated_at, version)
        VALUES (?, ?, ?, ?, 'REGULAR', ?, 'ACTIVE', '{}'::jsonb, true, now(), now(), 0)
        """,
        tenantId,
        "RET-" + unique,
        "ret-quote-" + suffix + "-" + unique,
        "Quote Retention " + suffix + " " + unique,
        "retention-" + suffix + "-" + unique + "@example.com");
  }

  private UUID insertQuote(UUID tenantId, String status, Instant createdAt, Instant updatedAt) {
    UUID quoteId = UUID.randomUUID();
    String unique = quoteId.toString();
    jdbc.update(
        """
        INSERT INTO sales.quote
          (id, tenant_id, uid, quote_number, customer_id, assigned_to_id, module_type,
           status, valid_until, attachments, is_active, created_at, updated_at, version)
        VALUES (?, ?, ?, ?, ?, ?, 'FABRIC', ?, current_date + 30, '[]'::jsonb,
                true, ?, ?, 0)
        """,
        quoteId,
        tenantId,
        "RET-Q-" + unique,
        "RET-Q-" + unique,
        UUID.randomUUID(),
        UUID.randomUUID(),
        status,
        Timestamp.from(createdAt),
        Timestamp.from(updatedAt));
    return quoteId;
  }

  private void insertQuoteLine(UUID tenantId, UUID quoteId) {
    UUID lineId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO sales.quote_line
          (id, tenant_id, uid, quote_id, product_desc, requested_qty, unit, list_price,
           offered_price, discount_rate, profit_margin, price_zone, module_specs,
           is_active, created_at, updated_at, version)
        VALUES (?, ?, ?, ?, 'Retention fixture', ?, 'METER', ?, ?, ?, ?, 'FREE',
                '{}'::jsonb, true, now(), now(), 0)
        """,
        lineId,
        tenantId,
        "RET-QL-" + lineId,
        quoteId,
        new BigDecimal("100.000"),
        new BigDecimal("10.0000"),
        new BigDecimal("9.0000"),
        new BigDecimal("0.1000"),
        new BigDecimal("0.2000"));
  }

  private UUID insertToken(
      UUID tenantId, UUID quoteId, String status, Instant createdAt, Instant usedAt) {
    UUID tokenId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO sales.quote_approval_token
          (id, tenant_id, uid, quote_id, token, channel, sent_to, expires_at, status,
           used_at, is_active, created_at, updated_at, version)
        VALUES (?, ?, ?, ?, ?, 'EMAIL', 'retention@example.com', ?, ?, ?, true, ?, ?, 0)
        """,
        tokenId,
        tenantId,
        "RET-QT-" + tokenId,
        quoteId,
        "retention-token-" + tokenId,
        Timestamp.from(createdAt.plus(7, ChronoUnit.DAYS)),
        status,
        usedAt == null ? null : Timestamp.from(usedAt),
        Timestamp.from(createdAt),
        Timestamp.from(createdAt));
    return tokenId;
  }

  private boolean rowExists(String table, UUID id) {
    Integer count =
        jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, id);
    return count != null && count == 1;
  }

  private void deleteTenantFixtures(UUID tenantId) {
    jdbc.update("DELETE FROM sales.quote_approval_token WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM sales.quote_line WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM sales.quote WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM common_tenant.common_tenant WHERE id = ?", tenantId);
  }
}
