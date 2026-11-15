package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.domain.event.SignupVerificationRequestedEvent;
import com.fabricmanagement.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.platform.auth.dto.SignupIntent;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * The signup retention job against real roles: it runs with no tenant, as {@code fabric_app} for
 * the global tables ({@code pending_signup}, {@code event_publication}) and through {@code
 * fabric_system} for the outbox.
 */
class PendingSignupRetentionIT extends AbstractFabricAppIntegrationTest {

  private static final String TOKEN = "q3Z8mV1xN0bH7cK2tW9yR4sL6pD5fG1jA8uE3iO0nTk";
  private static final String LINK = "https://app.example/setup?token=" + TOKEN;

  @Autowired private PendingSignupRetentionJob job;
  @Autowired private SignupRequestService signupRequests;

  private final List<UUID> signups = new ArrayList<>();
  private final List<UUID> publications = new ArrayList<>();
  private final List<UUID> mails = new ArrayList<>();
  private final List<String> emails = new ArrayList<>();
  private Instant now;

  @BeforeEach
  void setUp() {
    TenantContext.clear();
    now = Instant.now();
  }

  @AfterEach
  void cleanUp() {
    JdbcTemplate owner = owner();
    signups.forEach(id -> owner.update("DELETE FROM common_auth.pending_signup WHERE id = ?", id));
    publications.forEach(
        id -> owner.update("DELETE FROM public.event_publication WHERE id = ?", id));
    mails.forEach(
        id ->
            owner.update(
                "DELETE FROM common_communication.communication_email_outbox WHERE id = ?", id));
    for (String email : emails) {
      owner.update("DELETE FROM common_auth.pending_signup WHERE email = ?", email);
      owner.update(
          "DELETE FROM common_communication.communication_email_outbox WHERE recipient = ?", email);
    }
  }

  @Test
  @DisplayName("removes long-expired and old completed signups and keeps the rest")
  void purgesOnlyWhatRetentionAllows() {
    UUID expiredLongAgo = signup(now.minus(Duration.ofDays(8)), null);
    UUID expiredYesterday = signup(now.minus(Duration.ofDays(1)), null);
    UUID open = signup(now.plus(Duration.ofHours(20)), null);
    UUID completedLongAgo = signup(now.minus(Duration.ofDays(40)), now.minus(Duration.ofDays(31)));
    UUID completedRecently = signup(now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(3)));

    var counts = job.purgePendingSignups(now);

    assertThat(counts.expired()).isGreaterThanOrEqualTo(1);
    assertThat(counts.completed()).isGreaterThanOrEqualTo(1);
    assertThat(exists(expiredLongAgo)).isFalse();
    assertThat(exists(completedLongAgo)).isFalse();
    assertThat(exists(expiredYesterday)).isTrue();
    assertThat(exists(open)).isTrue();
    assertThat(exists(completedRecently)).isTrue();
  }

  @Test
  @DisplayName("removes delivered and dead signup-link publications, never other events")
  void removesSpentLinkPublications() {
    String signupEvent = SignupVerificationRequestedEvent.class.getName();
    UUID delivered = publication(signupEvent, now.minus(Duration.ofMinutes(5)), true);
    UUID undeliveredDead = publication(signupEvent, now.minus(Duration.ofHours(26)), false);
    UUID undeliveredFresh = publication(signupEvent, now.minus(Duration.ofMinutes(5)), false);
    UUID otherEvent = publication("com.example.OtherEvent", now.minus(Duration.ofMinutes(5)), true);

    job.removeSpentLinks(now);

    assertThat(publicationExists(delivered)).isFalse();
    assertThat(publicationExists(undeliveredDead)).isFalse();
    assertThat(publicationExists(undeliveredFresh)).isTrue();
    assertThat(publicationExists(otherEvent)).isTrue();
  }

  @Test
  @DisplayName("takes the link out of a mail's stored copy once it was sent a while ago")
  void redactsSentMailsAfterTheGracePeriod() {
    UUID sentLongAgo = mail("SENT", 30, 20);
    UUID sentJustNow = mail("SENT", 2, 1);
    UUID pendingFresh = mail("PENDING", 2, null);
    UUID failedDead = mail("FAILED", 26 * 60, null);
    // Still waiting to be sent: the worker must never send a redacted copy.
    UUID pendingDead = mail("PENDING", 26 * 60, null);
    UUID otherTenant = mail(UUID.randomUUID(), "SENT", 30, 20);

    job.removeSpentLinks(now);

    assertThat(body(sentLongAgo)).contains("/setup?token=removed").doesNotContain(TOKEN);
    assertThat(body(failedDead)).contains("/setup?token=removed").doesNotContain(TOKEN);
    assertThat(body(sentJustNow)).contains(TOKEN);
    assertThat(body(pendingFresh)).contains(TOKEN);
    assertThat(body(pendingDead)).contains(TOKEN);
    assertThat(body(otherTenant)).contains(TOKEN);
  }

  @Test
  @DisplayName("a real signup's publication is found by its event type and removed once delivered")
  void removesTheRealPublicationOfASignup() {
    String email = "retention." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    emails.add(email);
    signupRequests.request(
        SelfSignupRequest.builder()
            .organizationName("Willow Retention Ltd")
            .taxId("GB-RET-" + UUID.randomUUID().toString().substring(0, 8))
            .organizationType(OrganizationType.VERTICAL_MILL)
            .firstName("Eleanor")
            .lastName("Hughes")
            .email(email)
            .intent(SignupIntent.TRIAL)
            .acceptedTerms(true)
            .build());
    String eventType = SignupVerificationRequestedEvent.class.getName();
    // The mail is queued after commit, asynchronously; then the publication is completed.
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(() -> assertThat(deliveredPublications(eventType, email)).isPositive());

    job.removeSpentLinks(Instant.now());

    assertThat(deliveredPublications(eventType, email)).isZero();
  }

  private UUID signup(Instant expiresAt, Instant completedAt) {
    UUID id = UUID.randomUUID();
    String suffix = id.toString().substring(0, 8);
    owner()
        .update(
            """
            INSERT INTO common_auth.pending_signup
              (id, email, tax_id, request, token_hash, terms_accepted_at, expires_at, completed_at)
            VALUES (?, ?, ?, '{}'::jsonb, ?, now(), ?, ?)
            """,
            id,
            "retention." + suffix + "@example.com",
            "GB-RET-" + suffix,
            id.toString().replace("-", "") + id.toString().replace("-", ""),
            Timestamp.from(expiresAt),
            completedAt == null ? null : Timestamp.from(completedAt));
    signups.add(id);
    return id;
  }

  private UUID publication(String eventType, Instant publishedAt, boolean completed) {
    UUID id = UUID.randomUUID();
    owner()
        .update(
            """
            INSERT INTO public.event_publication
              (id, listener_id, event_type, serialized_event, publication_date, completion_date)
            VALUES (?, 'retention-test-listener', ?, ?, ?, ?)
            """,
            id,
            eventType,
            "{\"setupUrl\":\"" + LINK + "\"}",
            Timestamp.from(publishedAt),
            completed ? Timestamp.from(publishedAt.plusSeconds(1)) : null);
    publications.add(id);
    return id;
  }

  /** A signup mail queued {@code queuedMinutesAgo} and, when given, sent {@code sentMinutesAgo}. */
  private UUID mail(String status, int queuedMinutesAgo, Integer sentMinutesAgo) {
    return mail(TenantContext.SYSTEM_TENANT_ID, status, queuedMinutesAgo, sentMinutesAgo);
  }

  private UUID mail(UUID tenantId, String status, int queuedMinutesAgo, Integer sentMinutesAgo) {
    UUID id = UUID.randomUUID();
    owner()
        .update(
            """
            INSERT INTO common_communication.communication_email_outbox
              (id, tenant_id, uid, recipient, subject, html_body, status, created_at, updated_at,
               sent_at)
            VALUES (?, ?, ?, 'eleanor.hughes@example.com', 'Confirm your e-mail', ?, ?,
                    now() - (? * interval '1 minute'), now(),
                    now() - (CAST(? AS integer) * interval '1 minute'))
            """,
            id,
            tenantId,
            "RET-" + id,
            "<a href=\"" + LINK + "\">Finish setting up</a>",
            status,
            queuedMinutesAgo,
            sentMinutesAgo);
    mails.add(id);
    return id;
  }

  private boolean exists(UUID signupId) {
    return owner()
            .queryForObject(
                "SELECT count(*) FROM common_auth.pending_signup WHERE id = ?",
                Long.class,
                signupId)
        > 0;
  }

  private long deliveredPublications(String eventType, String email) {
    return owner()
        .queryForObject(
            "SELECT count(*) FROM public.event_publication WHERE event_type = ?"
                + " AND completion_date IS NOT NULL AND serialized_event LIKE ?",
            Long.class,
            eventType,
            "%" + email + "%");
  }

  private boolean publicationExists(UUID id) {
    return owner()
            .queryForObject(
                "SELECT count(*) FROM public.event_publication WHERE id = ?", Long.class, id)
        > 0;
  }

  private String body(UUID mailId) {
    return owner()
        .queryForObject(
            "SELECT html_body FROM common_communication.communication_email_outbox WHERE id = ?",
            String.class,
            mailId);
  }

  private static JdbcTemplate owner() {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
  }
}
