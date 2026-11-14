package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.platform.auth.dto.SignupIntent;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * SIGNUP-DEFER-1: a public signup writes one small pending row and an unverified lead, and nothing
 * else. Runs against the real {@code fabric_app} role (row-level security applies), as the
 * unauthenticated caller of the public endpoint does; the owner connection only inspects and cleans
 * up.
 */
class SignupRequestRealRolesIT extends AbstractFabricAppIntegrationTest {

  private static final List<String> TENANT_TABLES =
      List.of(
          "common_tenant.common_tenant",
          "common_company.common_organization",
          "common_user.common_user",
          "common_auth.common_registration_token");

  @Autowired private SignupRequestService signups;

  private final List<String> emails = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    // The container is shared with other real-role ITs: leave nothing behind.
    JdbcTemplate owner = owner();
    for (String email : emails) {
      owner.update("DELETE FROM common_auth.pending_signup WHERE email = ?", email);
      owner.update("DELETE FROM common_company.common_lead WHERE work_email = ?", email);
      // The membership foreign key no longer cascades (V20261031130000): memberships first.
      owner.update(
          "DELETE FROM common_auth.membership WHERE login_identity_id IN"
              + " (SELECT id FROM common_auth.login_identity WHERE email = ?)",
          email);
      owner.update("DELETE FROM common_auth.login_identity WHERE email = ?", email);
      owner.update(
          "DELETE FROM common_communication.communication_email_outbox WHERE recipient = ?", email);
    }
    emails.clear();
  }

  @Test
  @DisplayName("a signup leaves one pending row and an unverified lead, and no tenant data")
  void signupWritesOnlyThePendingRowAndAnUnverifiedLead() {
    Map<String, Long> before = tenantTableCounts();
    String email = freshEmail("owner");
    String taxId = "TAX-" + shortId();

    TenantContext.clear();
    signups.request(form(email, taxId, "Hawthorn Weaving"));

    assertThat(tenantTableCounts()).isEqualTo(before);
    Map<String, Object> pending = pendingRow(email);
    assertThat(pending.get("tax_id")).isEqualTo(taxId);
    assertThat(pending.get("completed_at")).isNull();
    assertThat(pending.get("send_count")).isEqualTo(1);
    // Only the hash of the secret is stored.
    assertThat((String) pending.get("token_hash")).hasSize(64);

    Map<String, Object> lead = leadRow(email);
    assertThat(lead.get("signup_id")).isEqualTo(pending.get("id"));
    assertThat(lead.get("company_name")).isEqualTo("Hawthorn Weaving");
    assertThat(lead.get("verified_at")).isNull();
    assertThat(lead.get("signup_tenant_id")).isNull();
    assertThat(lead.get("signup_snapshot")).isNull();
  }

  @Test
  @DisplayName("the verification mail reaches the outbox as a platform mail")
  void theLinkIsMailedFromThePlatformTenant() throws Exception {
    String email = freshEmail("mail");

    TenantContext.clear();
    signups.request(form(email, "TAX-" + shortId(), "Mail Probe"));

    Map<String, Object> mail = awaitOutboxMail(email);
    assertThat(mail.get("tenant_id")).isEqualTo(TenantContext.SYSTEM_TENANT_ID);
    assertThat((String) mail.get("html_body")).contains("/setup?token=").contains("Mail Probe");
  }

  @Test
  @DisplayName("the same address again replaces the row, keeps one lead and kills the old link")
  void sameAddressAgainReplaces() {
    String email = freshEmail("again");
    String firstTax = "TAX-" + shortId();
    String secondTax = "TAX-" + shortId();
    TenantContext.clear();
    signups.request(form(email, firstTax, "First Name Ltd"));
    Map<String, Object> first = pendingRow(email);

    signups.request(form(email, secondTax, "Second Name Ltd"));

    assertThat(countOpenPending(email)).isEqualTo(1);
    Map<String, Object> second = pendingRow(email);
    assertThat(second.get("id")).isEqualTo(first.get("id"));
    assertThat(second.get("token_hash")).isNotEqualTo(first.get("token_hash"));
    assertThat(second.get("tax_id")).isEqualTo(secondTax);
    assertThat(second.get("send_count")).isEqualTo(2);
    assertThat(
            owner()
                .queryForObject(
                    "SELECT request->>'organizationName' FROM common_auth.pending_signup WHERE email = ?",
                    String.class,
                    email))
        .isEqualTo("Second Name Ltd");
    // One lead, carrying the latest details.
    assertThat(
            owner()
                .queryForObject(
                    "SELECT count(*) FROM common_company.common_lead WHERE work_email = ?",
                    Integer.class,
                    email))
        .isEqualTo(1);
    assertThat(leadRow(email).get("company_name")).isEqualTo("Second Name Ltd");
  }

  @Test
  @DisplayName("a tax number held by another address's open signup is refused")
  void taxNumberHeldByAnotherAddress() {
    String taxId = "TAX-" + shortId();
    TenantContext.clear();
    signups.request(form(freshEmail("holder"), taxId, "Holder Ltd"));
    String other = freshEmail("other");

    // Whatever its letter case or spacing: the index sees the trimmed, lower-cased number.
    assertThatThrownBy(
            () -> signups.request(form(other, "  " + taxId.toLowerCase(Locale.ROOT) + " ", "X")))
        .isInstanceOf(TaxIdAlreadyExistsException.class);

    assertThat(countOpenPending(other)).isZero();
  }

  @Test
  @DisplayName("a taken tax number is refused the same way for an address with an account")
  void taxRefusalDoesNotDependOnTheAddress() {
    String taxId = "TAX-" + shortId();
    TenantContext.clear();
    signups.request(form(freshEmail("holder"), taxId, "Holder Ltd"));
    String accountHolder = freshEmail("account");
    owner()
        .update(
            "INSERT INTO common_auth.login_identity (id, email, password_hash) VALUES (?, ?, ?)",
            UUID.randomUUID(),
            accountHolder,
            "{noop}not-a-real-hash");
    String newcomer = freshEmail("newcomer");

    assertThatThrownBy(() -> signups.request(form(accountHolder, taxId, "X")))
        .isInstanceOf(TaxIdAlreadyExistsException.class);
    assertThatThrownBy(() -> signups.request(form(newcomer, taxId, "X")))
        .isInstanceOf(TaxIdAlreadyExistsException.class);
  }

  @Test
  @DisplayName("an address with a login identity gets no pending row and no lead")
  void addressWithAnAccountGetsNoPendingRow() throws Exception {
    String email = freshEmail("account");
    owner()
        .update(
            "INSERT INTO common_auth.login_identity (id, email, password_hash) VALUES (?, ?, ?)",
            UUID.randomUUID(),
            email,
            "{noop}not-a-real-hash");
    Map<String, Long> before = tenantTableCounts();

    TenantContext.clear();
    signups.request(form(email, "TAX-" + shortId(), "Account Holder Ltd"));

    assertThat(countOpenPending(email)).isZero();
    assertThat(
            owner()
                .queryForObject(
                    "SELECT count(*) FROM common_company.common_lead WHERE work_email = ?",
                    Integer.class,
                    email))
        .isZero();
    assertThat(tenantTableCounts()).isEqualTo(before);
    assertThat((String) awaitOutboxMail(email).get("html_body")).contains("My organisations");
  }

  @Test
  @DisplayName("terms that are not accepted store nothing")
  void termsNotAccepted() {
    String email = freshEmail("terms");
    SelfSignupRequest form = form(email, "TAX-" + shortId(), "No Terms Ltd");
    form.setAcceptedTerms(false);

    TenantContext.clear();
    assertThatThrownBy(() -> signups.request(form)).isInstanceOf(PlatformDomainException.class);

    assertThat(countOpenPending(email)).isZero();
  }

  @Test
  @DisplayName("an expired signup no longer holds its address or its tax number")
  void expiredSignupDoesNotBlock() {
    String email = freshEmail("expired");
    String taxId = "TAX-" + shortId();
    TenantContext.clear();
    signups.request(form(email, taxId, "Expired Ltd"));
    Object oldId = pendingRow(email).get("id");
    owner()
        .update(
            "UPDATE common_auth.pending_signup SET expires_at = now() - interval '1 hour'"
                + " WHERE email = ?",
            email);

    // Another address may now use the tax number, and the first address may sign up again.
    String other = freshEmail("later");
    signups.request(form(other, taxId, "Later Ltd"));
    assertThat(countOpenPending(other)).isEqualTo(1);
    assertThat(countOpenPending(email)).isZero();

    signups.request(form(email, "TAX-" + shortId(), "Expired Ltd Again"));
    assertThat(pendingRow(email).get("id")).isNotEqualTo(oldId);
  }

  @Test
  @DisplayName("two simultaneous signups with the same address leave one row")
  void simultaneousSignupsWithTheSameAddress() throws Exception {
    String email = freshEmail("race");

    List<Throwable> failures =
        runTogether(
            () -> signups.request(form(email, "TAX-" + shortId(), "Race A")),
            () -> signups.request(form(email, "TAX-" + shortId(), "Race B")));

    assertThat(failures).isEmpty();
    assertThat(countOpenPending(email)).isEqualTo(1);
    assertThat(
            owner()
                .queryForObject(
                    "SELECT count(*) FROM common_company.common_lead WHERE work_email = ?",
                    Integer.class,
                    email))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("two simultaneous signups with the same tax number: one wins, one is refused")
  void simultaneousSignupsWithTheSameTaxNumber() throws Exception {
    String taxId = "TAX-" + shortId();
    String a = freshEmail("taxa");
    String b = freshEmail("taxb");

    List<Throwable> failures =
        runTogether(
            () -> signups.request(form(a, taxId, "Tax A")),
            () -> signups.request(form(b, taxId, "Tax B")));

    assertThat(failures).hasSize(1);
    assertThat(failures.get(0)).isInstanceOf(TaxIdAlreadyExistsException.class);
    assertThat(countOpenPending(a) + countOpenPending(b)).isEqualTo(1);
  }

  // ---- helpers -------------------------------------------------------------------------------

  private List<Throwable> runTogether(Runnable first, Runnable second) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<Throwable>> results = new ArrayList<>();
      for (Runnable task : List.of(first, second)) {
        results.add(
            pool.submit(
                () -> {
                  go.await();
                  try {
                    task.run();
                    return null;
                  } catch (Throwable failure) {
                    return failure;
                  }
                }));
      }
      go.countDown();
      List<Throwable> failures = new ArrayList<>();
      for (Future<Throwable> result : results) {
        Throwable failure = result.get(30, TimeUnit.SECONDS);
        if (failure != null) failures.add(failure);
      }
      return failures;
    } finally {
      pool.shutdownNow();
    }
  }

  private Map<String, Object> awaitOutboxMail(String recipient) throws Exception {
    // The mail is queued by an asynchronous after-commit listener.
    for (int i = 0; i < 100; i++) {
      List<Map<String, Object>> rows =
          owner()
              .queryForList(
                  "SELECT tenant_id, subject, html_body FROM"
                      + " common_communication.communication_email_outbox WHERE recipient = ?",
                  recipient);
      if (!rows.isEmpty()) return rows.get(0);
      Thread.sleep(100);
    }
    throw new AssertionError("No mail was queued for " + recipient);
  }

  private Map<String, Long> tenantTableCounts() {
    Map<String, Long> counts = new java.util.LinkedHashMap<>();
    for (String table : TENANT_TABLES) {
      counts.put(table, owner().queryForObject("SELECT count(*) FROM " + table, Long.class));
    }
    return counts;
  }

  private Map<String, Object> pendingRow(String email) {
    return owner().queryForMap("SELECT * FROM common_auth.pending_signup WHERE email = ?", email);
  }

  private int countOpenPending(String email) {
    return owner()
        .queryForObject(
            "SELECT count(*) FROM common_auth.pending_signup WHERE email = ?"
                + " AND completed_at IS NULL",
            Integer.class,
            email);
  }

  private Map<String, Object> leadRow(String email) {
    return owner()
        .queryForMap("SELECT * FROM common_company.common_lead WHERE work_email = ?", email);
  }

  private static SelfSignupRequest form(String email, String taxId, String company) {
    return SelfSignupRequest.builder()
        .organizationName(company)
        .taxId(taxId)
        .organizationType(OrganizationType.VERTICAL_MILL)
        .firstName("Eleanor")
        .lastName("Hawthorn")
        .email(email)
        .intent(SignupIntent.PLAYGROUND)
        .acceptedTerms(true)
        .build();
  }

  private String freshEmail(String local) {
    String email = (local + "-" + shortId() + "@example.com").toLowerCase(Locale.ROOT);
    emails.add(email);
    return email;
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }

  private static JdbcTemplate owner() {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
  }
}
