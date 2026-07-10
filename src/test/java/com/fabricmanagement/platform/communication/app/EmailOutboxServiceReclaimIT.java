package com.fabricmanagement.platform.communication.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class EmailOutboxServiceReclaimIT extends AbstractIntegrationTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Autowired private EmailOutboxService service;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    cleanup();
    ReflectionTestUtils.setField(service, "emailOutboxWorkerEnabled", true);
    ReflectionTestUtils.setField(service, "reclaimAfterMinutes", 15);
  }

  @AfterEach
  void tearDown() {
    ReflectionTestUtils.setField(service, "emailOutboxWorkerEnabled", false);
    cleanup();
  }

  @Test
  void reclaimsOnlySendingRowsOlderThanLeaseAndBumpsVersion() {
    UUID staleId = UUID.randomUUID();
    UUID freshId = UUID.randomUUID();
    insertSending(staleId, "MAIL2-STALE-" + staleId, 7_200);
    insertSending(freshId, "MAIL2-FRESH-" + freshId, 30);

    service.reclaimStuckSendingEmails();

    Map<String, Object> stale = row(staleId);
    assertThat(stale.get("status")).isEqualTo("PENDING");
    assertThat(((Number) stale.get("version")).longValue()).isEqualTo(1L);
    assertThat(stale.get("next_retry_at")).isNotNull();

    Map<String, Object> fresh = row(freshId);
    assertThat(fresh.get("status")).isEqualTo("SENDING");
    assertThat(((Number) fresh.get("version")).longValue()).isZero();
    assertThat(fresh.get("next_retry_at")).isNull();
  }

  private void insertSending(UUID id, String uid, int secondsOld) {
    jdbc.update(
        """
        INSERT INTO common_communication.communication_email_outbox (
          id, tenant_id, uid, recipient, subject, html_body, status, retry_count, max_retries,
          is_active, created_at, updated_at, version
        )
        VALUES (
          ?, ?, ?, ?, 'Subject', '<p>Body</p>', 'SENDING', 0, 3,
          true, now() - (? * interval '1 second'), now() - (? * interval '1 second'), 0
        )
        """,
        id,
        TENANT_ID,
        uid,
        "recipient@example.com",
        secondsOld,
        secondsOld);
  }

  private Map<String, Object> row(UUID id) {
    return jdbc.queryForMap(
        """
        SELECT status, version, next_retry_at
        FROM common_communication.communication_email_outbox
        WHERE id = ?
        """,
        id);
  }

  private void cleanup() {
    jdbc.update(
        "DELETE FROM common_communication.communication_email_outbox WHERE uid LIKE 'MAIL2-%'");
  }
}
