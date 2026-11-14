package com.fabricmanagement.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.platform.auth.app.SignupLinkToken;
import com.fabricmanagement.platform.auth.domain.PendingSignup;
import com.fabricmanagement.platform.auth.domain.PendingSignupRequest;
import com.fabricmanagement.platform.auth.dto.SignupIntent;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * SIGNUP-DEFER-1: the signup form no longer builds a company, the e-mailed link does. Tests that
 * need a company go through the same two steps a person does, or, when they only need the tenant,
 * call the verified-signup builder directly.
 *
 * <p>The raw link token only exists in the mail, and the mail is not delivered in tests, so {@link
 * #knownLinkToken} re-keys the pending row to a token the test knows. The row is otherwise exactly
 * what the form wrote.
 */
public final class PendingSignupFixtures {

  /** A password that satisfies the setup form. */
  public static final String PASSWORD = "Hawthorn!Secret27";

  private PendingSignupFixtures() {}

  /**
   * Replaces the link token of the open pending signup of {@code email} with one the test knows.
   *
   * @return the raw token, as it would appear in the link
   */
  public static String knownLinkToken(JdbcTemplate jdbc, String email) {
    String raw = "test-link-" + UUID.randomUUID();
    int updated =
        jdbc.update(
            "UPDATE common_auth.pending_signup SET token_hash = ?, updated_at = now()"
                + " WHERE email = ? AND completed_at IS NULL",
            SignupLinkToken.hash(raw),
            normalise(email));
    if (updated != 1) {
      throw new AssertionError(
          "Expected one open pending signup for " + email + ", got " + updated);
    }
    return raw;
  }

  /**
   * Stores an open pending signup, as the signup form would, and returns the raw link token. For
   * tests that need a pending signup without going through the form (and its duplicate checks).
   */
  public static String insert(JdbcTemplate jdbc, String email, String taxId) {
    String raw = "test-link-" + UUID.randomUUID();
    String request =
        """
        {"organizationName":"Pending Probe %s","organizationType":"VERTICAL_MILL",
         "firstName":"Harriet","lastName":"Lowe","selectedOS":null,"intent":"PLAYGROUND"}
        """
            .formatted(taxId);
    jdbc.update(
        "INSERT INTO common_auth.pending_signup (id, email, tax_id, request, token_hash,"
            + " terms_accepted_at, expires_at) VALUES (?, ?, ?, ?::jsonb, ?, now(),"
            + " now() + interval '1 day')",
        UUID.randomUUID(),
        normalise(email),
        taxId,
        request,
        SignupLinkToken.hash(raw));
    return raw;
  }

  /** The setup page's call: use the link, choosing {@code password} (null: none sent). */
  public static MockHttpServletRequestBuilder useLink(String rawToken, String password) {
    String body =
        password == null
            ? "{\"token\":\"%s\"}".formatted(rawToken)
            : "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(rawToken, password);
    return post("/api/v1/auth/setup-password")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  /**
   * Uses the link of an already submitted signup form and returns the new tenant's id. The form
   * must have been posted for {@code email} and not completed yet.
   */
  public static UUID completeSignup(MockMvc mockMvc, JdbcTemplate jdbc, String email)
      throws Exception {
    return completeSignup(mockMvc, jdbc, email, PASSWORD);
  }

  /** As {@link #completeSignup(MockMvc, JdbcTemplate, String)}, with the person's own password. */
  public static UUID completeSignup(
      MockMvc mockMvc, JdbcTemplate jdbc, String email, String password) throws Exception {
    String token = knownLinkToken(jdbc, email);
    mockMvc.perform(useLink(token, password)).andExpect(status().isOk());
    return completedTenantId(jdbc, email);
  }

  /** The tenant the signup of {@code email} built, once its link was used. */
  public static UUID completedTenantId(JdbcTemplate jdbc, String email) {
    return jdbc.queryForObject(
        "SELECT completed_tenant_id FROM common_auth.pending_signup"
            + " WHERE email = ? AND completed_at IS NOT NULL"
            + " ORDER BY completed_at DESC LIMIT 1",
        UUID.class,
        normalise(email));
  }

  /**
   * A verified self-service signup in memory (not stored), for tests that call {@code
   * TenantOnboardingService#createVerifiedSelfServiceTenant} directly.
   */
  public static PendingSignup pendingSignup(
      String organizationName,
      String taxId,
      OrganizationType organizationType,
      String firstName,
      String lastName,
      String email,
      SignupIntent intent) {
    PendingSignup pending = BeanUtils.instantiateClass(PendingSignup.class);
    Instant now = Instant.now();
    ReflectionTestUtils.setField(pending, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(pending, "email", normalise(email));
    ReflectionTestUtils.setField(pending, "taxId", taxId);
    ReflectionTestUtils.setField(
        pending,
        "request",
        new PendingSignupRequest(
            organizationName, organizationType, firstName, lastName, null, intent.name()));
    ReflectionTestUtils.setField(
        pending, "tokenHash", SignupLinkToken.hash(UUID.randomUUID().toString()));
    ReflectionTestUtils.setField(pending, "termsAcceptedAt", now.minusSeconds(60));
    ReflectionTestUtils.setField(pending, "expiresAt", now.plus(1, ChronoUnit.DAYS));
    return pending;
  }

  private static String normalise(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
