package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.PasswordSetupRequest;
import com.fabricmanagement.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.platform.auth.dto.SetupLinkStatus;
import com.fabricmanagement.platform.auth.dto.SignupIntent;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import com.fabricmanagement.testsupport.PendingSignupFixtures;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * SIGNUP-DEFER-1: using the e-mailed link builds the company, the login and the membership in one
 * transaction. Runs against the real {@code fabric_app} role (row-level security applies), as the
 * unauthenticated caller of the setup page does; the owner connection only inspects and cleans up.
 *
 * <p>The failing-step case (nothing left behind, the same link works afterwards) is {@link
 * com.fabricmanagement.platform.auth.onboarding.OnboardingAtomicityIT}, which owns the step spy.
 */
class SignupCompletionRealRolesIT extends AbstractFabricAppIntegrationTest {

  private static final String IP = "127.0.0.1";
  private static final String PASSWORD = "Hawthorn!Secret27";

  @Autowired private SignupRequestService signups;
  @Autowired private PasswordSetupService setup;
  @Autowired private PasswordEncoder passwordEncoder;

  private final List<String> emails = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    // The container is shared with other real-role ITs: leave no pending signup or login behind.
    // (Tenants and leads stay, as after any other signup IT.)
    JdbcTemplate owner = owner();
    for (String email : emails) {
      owner.update("DELETE FROM common_auth.pending_signup WHERE email = ?", email);
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
  @DisplayName("the link builds the company, the login and the membership and signs the person in")
  void theLinkBuildsTheCompanyAndTheLogin() throws Exception {
    String email = freshEmail("owner");
    String taxId = "TAX-" + shortId();
    String company = "Hawthorn Weaving " + shortId();
    String link = submitForm(email, taxId, company);
    // The verification mail is queued after commit; wait for it so the mail count below is exact.
    awaitOutboxMail(email);
    assertThat(tenantCountByName(company)).as("no company before the link is used").isZero();
    UUID pendingId =
        owner()
            .queryForObject(
                "SELECT id FROM common_auth.pending_signup WHERE email = ?", UUID.class, email);

    LoginResponse response = setup.setupPassword(request(link, PASSWORD), IP);

    assertThat(response.getAccessToken()).isNotBlank();
    assertThat(response.getRefreshToken()).isNotBlank();
    assertThat(response.getSignInRequired()).isNull();
    assertThat(response.getNeedsOnboarding()).isTrue();
    assertThat(response.getOnboardingPrefill().getPrimaryEmail()).isEqualTo(email);
    assertThat(response.getOnboardingPrefill().getOrganizationName()).isEqualTo(company);
    assertThat(response.getOnboardingPrefill().getTaxId()).isEqualTo(taxId);

    Map<String, Object> pending = pendingRow(email);
    assertThat(pending.get("completed_at")).isNotNull();
    UUID tenantId = (UUID) pending.get("completed_tenant_id");
    assertThat(tenantId).isNotNull();
    assertThat(tenantCountByName(company)).isEqualTo(1L);
    assertThat(
            owner()
                .queryForObject(
                    "SELECT name FROM common_tenant.common_tenant WHERE id = ?",
                    String.class,
                    tenantId))
        .isEqualTo(company);
    // A trial signup starts its trial clock when the link is used, inside the same transaction.
    Map<String, Object> trial =
        owner()
            .queryForMap(
                "SELECT status, trial_started_at, trial_ends_at, demo_mode"
                    + " FROM common_tenant.common_tenant WHERE id = ?",
                tenantId);
    assertThat(trial.get("status")).isEqualTo("TRIAL");
    assertThat(trial.get("demo_mode")).isEqualTo(false);
    assertThat(trial.get("trial_started_at")).as("trial started at completion").isNotNull();
    assertThat(trial.get("trial_ends_at")).isNotNull();
    assertThat(
            owner()
                .queryForObject(
                    "SELECT count(*) FROM common_company.common_organization"
                        + " WHERE tenant_id = ? AND tax_id = ?",
                    Long.class,
                    tenantId,
                    taxId))
        .isEqualTo(1L);

    UUID adminId = adminUserId(email);
    Map<String, Object> identity =
        owner().queryForMap("SELECT * FROM common_auth.login_identity WHERE email = ?", email);
    assertThat(identity.get("email_verified")).isEqualTo(true);
    assertThat(identity.get("requires_password_reset")).isEqualTo(false);
    assertThat(passwordEncoder.matches(PASSWORD, (String) identity.get("password_hash"))).isTrue();

    Map<String, Object> membership =
        owner()
            .queryForMap(
                "SELECT * FROM common_auth.membership WHERE login_identity_id = ?",
                identity.get("id"));
    assertThat(membership.get("tenant_id")).isEqualTo(tenantId);
    assertThat(membership.get("user_id")).isEqualTo(adminId);
    assertThat(membership.get("status")).isEqualTo("ACTIVE");
    assertThat(membership.get("is_default")).isEqualTo(true);
    assertThat(
            owner()
                .queryForObject(
                    "SELECT password_hash FROM common_auth.common_auth_user WHERE user_id = ?",
                    String.class,
                    adminId))
        .isEqualTo(identity.get("password_hash"));

    // The lead the form recorded is the one that is now verified and linked to the tenant.
    Map<String, Object> lead =
        owner().queryForMap("SELECT * FROM common_company.common_lead WHERE work_email = ?", email);
    assertThat(lead.get("signup_id")).isEqualTo(pendingId);
    assertThat(lead.get("verified_at")).isNotNull();
    assertThat(lead.get("signup_tenant_id")).isEqualTo(tenantId);
    assertThat(lead.get("signup_user_id")).isEqualTo(adminId);
    assertThat(lead.get("signup_snapshot")).isNotNull();
    assertThat(lead.get("trial_tenant_id")).isEqualTo(tenantId);

    // No second mail: no "added to your company" mail, no setup-link mail. Only the verification
    // mail of the form is in the outbox.
    awaitTenantEventsSettled(tenantId);
    assertThat(outboxMailCount(email)).isEqualTo(1L);
  }

  @Test
  @DisplayName("an expired link builds nothing and is reported invalid")
  void anExpiredLinkIsRefused() throws Exception {
    String email = freshEmail("expired");
    String company = "Expired Weaving " + shortId();
    String link = submitForm(email, "TAX-" + shortId(), company);
    owner()
        .update(
            "UPDATE common_auth.pending_signup SET expires_at = now() - interval '1 minute'"
                + " WHERE email = ?",
            email);

    assertThat(setup.checkLink(link)).isEqualTo(SetupLinkStatus.invalid());
    assertThatThrownBy(() -> setup.setupPassword(request(link, PASSWORD), IP))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure -> {
              assertThat(failure.getErrorCode()).isEqualTo("AUTH_TOKEN_INVALID");
              assertThat(failure.getHttpStatus()).isEqualTo(400);
            });

    assertThat(tenantCountByName(company)).isZero();
    assertThat(identityCount(email)).isZero();
  }

  @Test
  @DisplayName("a used link cannot build a second company")
  void aUsedLinkIsRefused() throws Exception {
    String email = freshEmail("used");
    String company = "Used Weaving " + shortId();
    String link = submitForm(email, "TAX-" + shortId(), company);
    setup.setupPassword(request(link, PASSWORD), IP);

    assertThat(setup.checkLink(link)).isEqualTo(SetupLinkStatus.invalid());
    assertThatThrownBy(() -> setup.setupPassword(request(link, PASSWORD), IP))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo("AUTH_TOKEN_INVALID"));

    assertThat(tenantCountByName(company)).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "a login created in the meantime keeps its password and the person signs in normally")
  void anIdentityCreatedInTheMeantimeIsKept() throws Exception {
    String email = freshEmail("invited");
    String company = "Invited Weaving " + shortId();
    String link = submitForm(email, "TAX-" + shortId(), company);
    // The address was invited to another company after the form was sent and has a login now.
    owner()
        .update(
            "INSERT INTO common_auth.login_identity (id, email, password_hash, email_verified)"
                + " VALUES (?, ?, ?, true)",
            UUID.randomUUID(),
            email,
            "{noop}existing-hash");

    assertThat(setup.checkLink(link)).isEqualTo(new SetupLinkStatus(true, false));
    // A password typed on the page is not used: the existing login's password stays.
    LoginResponse response = setup.setupPassword(request(link, PASSWORD), IP);

    assertThat(response.getSignInRequired()).isTrue();
    assertThat(response.getAccessToken()).isNull();
    assertThat(response.getRefreshToken()).isNull();
    assertThat(tenantCountByName(company)).isEqualTo(1L);

    Map<String, Object> identity =
        owner().queryForMap("SELECT * FROM common_auth.login_identity WHERE email = ?", email);
    assertThat(identity.get("password_hash")).isEqualTo("{noop}existing-hash");
    assertThat(identity.get("requires_password_reset")).isEqualTo(false);

    UUID tenantId = (UUID) pendingRow(email).get("completed_tenant_id");
    UUID adminId = adminUserId(email);
    assertThat(
            owner()
                .queryForObject(
                    "SELECT count(*) FROM common_auth.membership"
                        + " WHERE login_identity_id = ? AND tenant_id = ? AND user_id = ?",
                    Long.class,
                    identity.get("id"),
                    tenantId,
                    adminId))
        .isEqualTo(1L);
    assertThat(
            owner()
                .queryForObject(
                    "SELECT password_hash FROM common_auth.common_auth_user WHERE user_id = ?",
                    String.class,
                    adminId))
        .isEqualTo("{noop}existing-hash");
    // No session was started by the link.
    assertThat(
            owner()
                .queryForObject(
                    "SELECT count(*) FROM common_auth.common_refresh_token WHERE user_id = ?",
                    Long.class,
                    adminId))
        .isZero();
  }

  @Test
  @DisplayName("a double click on the link builds one company; the other request is refused")
  void aDoubleClickBuildsOneCompany() throws Exception {
    String email = freshEmail("double");
    String company = "Double Weaving " + shortId();
    String link = submitForm(email, "TAX-" + shortId(), company);

    List<Object> outcomes = raceTwice(() -> setup.setupPassword(request(link, PASSWORD), IP));

    long successes = outcomes.stream().filter(outcome -> outcome instanceof LoginResponse).count();
    List<Throwable> failures =
        outcomes.stream().filter(Throwable.class::isInstance).map(Throwable.class::cast).toList();
    assertThat(successes).as("requests that completed the signup").isEqualTo(1L);
    assertThat(failures).hasSize(1);
    assertThat(failures.getFirst())
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo("AUTH_TOKEN_INVALID"));
    assertThat(tenantCountByName(company)).isEqualTo(1L);
    assertThat(identityCount(email)).isEqualTo(1L);
  }

  // ---------------------------------------------------------------------------------------------

  /** The form, as the public endpoint takes it; returns a link token the test knows. */
  private String submitForm(String email, String taxId, String company) {
    TenantContext.clear();
    signups.request(
        SelfSignupRequest.builder()
            .organizationName(company)
            .taxId(taxId)
            .organizationType(OrganizationType.VERTICAL_MILL)
            .firstName("Eleanor")
            .lastName("Hawthorn")
            .email(email)
            .intent(SignupIntent.TRIAL)
            .acceptedTerms(true)
            .build());
    return PendingSignupFixtures.knownLinkToken(owner(), email);
  }

  private static PasswordSetupRequest request(String link, String password) {
    return PasswordSetupRequest.builder().token(link).password(password).build();
  }

  /** Two requests that start at the same moment; each outcome is the result or the exception. */
  private List<Object> raceTwice(Callable<Object> request) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch go = new CountDownLatch(1);
      List<Future<Object>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        futures.add(
            pool.submit(
                () -> {
                  go.await();
                  try {
                    return request.call();
                  } catch (Throwable failure) {
                    return failure;
                  }
                }));
      }
      go.countDown();
      List<Object> outcomes = new ArrayList<>();
      for (Future<Object> future : futures) {
        outcomes.add(future.get(180, TimeUnit.SECONDS));
      }
      return outcomes;
    } finally {
      pool.shutdownNow();
    }
  }

  private UUID adminUserId(String email) {
    return owner()
        .queryForObject(
            """
            SELECT u.id
            FROM common_user.common_user u
            JOIN common_user.common_user_contact uc ON uc.user_id = u.id
            JOIN common_communication.common_contact c ON c.id = uc.contact_id
            WHERE lower(c.contact_value) = lower(?)
            """,
            UUID.class,
            email);
  }

  private Map<String, Object> pendingRow(String email) {
    return owner().queryForMap("SELECT * FROM common_auth.pending_signup WHERE email = ?", email);
  }

  private long tenantCountByName(String name) {
    return owner()
        .queryForObject(
            "SELECT count(*) FROM common_tenant.common_tenant WHERE name = ?", Long.class, name);
  }

  private long identityCount(String email) {
    return owner()
        .queryForObject(
            "SELECT count(*) FROM common_auth.login_identity WHERE email = ?", Long.class, email);
  }

  private long outboxMailCount(String recipient) {
    return owner()
        .queryForObject(
            "SELECT count(*) FROM common_communication.communication_email_outbox"
                + " WHERE recipient = ?",
            Long.class,
            recipient);
  }

  private void awaitOutboxMail(String recipient) {
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(() -> assertThat(outboxMailCount(recipient)).isPositive());
  }

  /** Every event publication of the tenant has been delivered, and stays so for a moment. */
  private void awaitTenantEventsSettled(UUID tenantId) {
    await()
        .atMost(Duration.ofSeconds(120))
        .during(Duration.ofMillis(500))
        .untilAsserted(
            () ->
                assertThat(
                        owner()
                            .queryForObject(
                                "SELECT count(*) FROM event_publication"
                                    + " WHERE serialized_event LIKE ? AND completion_date IS NULL",
                                Integer.class,
                                "%" + tenantId + "%"))
                    .isZero());
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
