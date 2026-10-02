package com.fabricmanagement.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.MfaType;
import com.fabricmanagement.platform.auth.domain.VerificationCode;
import com.fabricmanagement.platform.auth.domain.VerificationType;
import com.fabricmanagement.platform.auth.infra.repository.LoginIdentityRepository;
import com.fabricmanagement.platform.auth.infra.repository.VerificationCodeRepository;
import com.fabricmanagement.platform.communication.app.VerificationService;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Checks, over real HTTP handling and a real PostgreSQL, that the login and password-reset
 * endpoints answer exactly the same way whether or not an account exists, and that the counters
 * behind the lockout and the code attempt limit are persisted even though each rejection rolls back
 * the request transaction.
 *
 * <p>Emails are not sent: {@link VerificationService} is mocked, and the code it would have sent is
 * captured from the mock.
 */
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Account enumeration: login and password reset")
class AuthAccountEnumerationIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "Correct-Horse-1!";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private LoginIdentityRepository loginIdentityRepository;
  @Autowired private VerificationCodeRepository verificationCodeRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private VerificationService verificationService;

  private String existingEmail;
  private String missingEmail;
  private LoginIdentity identity;

  @BeforeEach
  void createAccount() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    existingEmail = "enum-" + suffix + "@example.com";
    missingEmail = "nobody-" + suffix + "@example.com";
    identity =
        loginIdentityRepository.save(
            LoginIdentity.builder()
                .email(existingEmail)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .isMfaEnabled(false)
                .primaryMfaType(MfaType.NONE)
                .isActive(true)
                .emailVerified(true)
                .failedLoginAttempts(0)
                .requiresPasswordReset(false)
                .build());
  }

  @AfterEach
  void deleteAccount() {
    storedCode(existingEmail).ifPresent(verificationCodeRepository::delete);
    loginIdentityRepository.deleteById(identity.getId());
    TenantContext.clear();
  }

  // ── Login ──────────────────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("POST /login: a missing account and a wrong password produce the same response")
  void login_missingAccountAndWrongPassword_sameResponse() throws Exception {
    MvcResult missing = login(missingEmail, "wrong-password");
    MvcResult wrongPassword = login(existingEmail, "wrong-password");

    assertThat(missing.getResponse().getStatus()).isEqualTo(401);
    assertThat(comparable(missing)).isEqualTo(comparable(wrongPassword));
    assertThat(body(missing).path("code").asText()).isEqualTo("AUTH_INVALID_CREDENTIALS");
    assertThat(body(missing).toString()).doesNotContain(existingEmail, missingEmail, "example");
  }

  @Test
  @DisplayName("POST /login: failed attempts persist, and the lock rejects the right password")
  void login_failedAttemptsPersist_andLockedAccountAnswersLikeWrongPassword() throws Exception {
    login(existingEmail, "wrong-password");
    login(existingEmail, "wrong-password");

    LoginIdentity afterTwo = loginIdentityRepository.findById(identity.getId()).orElseThrow();
    assertThat(afterTwo.getFailedLoginAttempts())
        .as("the rejection rolls back the request transaction; the counter must survive it")
        .isEqualTo(2);

    // Default lockout is 5 attempts (auth.lockout.max-attempts); go well past it.
    for (int i = 0; i < 6; i++) {
      login(existingEmail, "wrong-password");
    }
    LoginIdentity locked = loginIdentityRepository.findById(identity.getId()).orElseThrow();
    assertThat(locked.isLocked()).isTrue();

    MvcResult rightPasswordWhileLocked = login(existingEmail, PASSWORD);
    MvcResult missing = login(missingEmail, PASSWORD);
    assertThat(rightPasswordWhileLocked.getResponse().getStatus()).isEqualTo(401);
    assertThat(comparable(rightPasswordWhileLocked)).isEqualTo(comparable(missing));
  }

  // ── Password reset ─────────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("POST /password-reset/request: same 200 for any address; code stored for real ones")
  void resetRequest_sameResponse_andCodePersistedForExistingAccount() throws Exception {
    MvcResult existing = resetRequest(existingEmail);
    MvcResult missing = resetRequest(missingEmail);

    assertThat(existing.getResponse().getStatus()).isEqualTo(200);
    assertThat(comparable(existing)).isEqualTo(comparable(missing));

    // The anonymous request has no TenantContext; the code row must still be written (under the
    // system tenant) or nothing is ever sent while the caller is told "sent".
    assertThat(storedCode(existingEmail)).isPresent();
    assertThat(storedCode(missingEmail)).isEmpty();
    verify(verificationService, timeout(2_000))
        .sendVerificationCode(
            eq(existingEmail), anyString(), any(), any(), eq(VerificationType.PASSWORD_RESET));
    verify(verificationService, never())
        .sendVerificationCode(eq(missingEmail), anyString(), any(), any(), any());
  }

  @Test
  @DisplayName("POST /password-reset/verify: wrong attempts persist and exhaust the right code")
  void resetVerify_wrongAttemptsPersist_andLimitRejectsTheRightCode() throws Exception {
    resetRequest(existingEmail);
    String code = sentCode(existingEmail);
    String wrongCode = code.equals("000000") ? "111111" : "000000";

    MvcResult missing = resetVerify(missingEmail, wrongCode);
    MvcResult firstWrong = resetVerify(existingEmail, wrongCode);
    assertThat(firstWrong.getResponse().getStatus()).isEqualTo(400);
    assertThat(comparable(firstWrong)).isEqualTo(comparable(missing));
    assertThat(body(firstWrong).path("code").asText()).isEqualTo("AUTH_VERIFICATION_CODE_INVALID");

    assertThat(storedCode(existingEmail).orElseThrow().getAttemptCount())
        .as("the rejection rolls back the request transaction; the attempt must survive it")
        .isEqualTo(1);

    resetVerify(existingEmail, wrongCode);
    resetVerify(existingEmail, wrongCode);
    assertThat(storedCode(existingEmail).orElseThrow().getAttemptCount()).isEqualTo(3);

    // Default limit is 3 attempts (application.verification.max-attempts).
    MvcResult rightCodeAfterLimit = resetVerify(existingEmail, code);
    assertThat(rightCodeAfterLimit.getResponse().getStatus()).isEqualTo(400);
    assertThat(comparable(rightCodeAfterLimit)).isEqualTo(comparable(missing));

    LoginIdentity unchanged = loginIdentityRepository.findById(identity.getId()).orElseThrow();
    assertThat(passwordEncoder.matches(PASSWORD, unchanged.getPasswordHash()))
        .as("the password must not change once the code is exhausted")
        .isTrue();
  }

  @Test
  @DisplayName("POST /password-reset/verify: the right code sets the password; login uses it")
  void resetVerify_rightCode_setsPassword_thenLoginWorksWithIt() throws Exception {
    resetRequest(existingEmail);
    String code = sentCode(existingEmail);
    String newPassword = "Another-Horse-2!";

    MvcResult reset = resetVerify(existingEmail, code);
    assertThat(reset.getResponse().getStatus()).isEqualTo(200);
    assertThat(reset.getResponse().getContentAsString()).doesNotContain("accessToken");

    LoginIdentity updated = loginIdentityRepository.findById(identity.getId()).orElseThrow();
    assertThat(passwordEncoder.matches(newPassword, updated.getPasswordHash())).isTrue();

    // The old password is now a wrong password: same answer as a missing account.
    MvcResult oldPassword = login(existingEmail, PASSWORD);
    assertThat(oldPassword.getResponse().getStatus()).isEqualTo(401);
    assertThat(body(oldPassword).path("code").asText()).isEqualTo("AUTH_INVALID_CREDENTIALS");
  }

  // ── helpers ────────────────────────────────────────────────────────────────────────────────────

  private MvcResult login(String email, String password) throws Exception {
    return postJson(
        "/api/v1/auth/login",
        objectMapper.createObjectNode().put("contactValue", email).put("password", password));
  }

  private MvcResult resetRequest(String email) throws Exception {
    return postJson(
        "/api/v1/auth/password-reset/request",
        objectMapper.createObjectNode().put("contactValue", email));
  }

  private MvcResult resetVerify(String email, String code) throws Exception {
    return postJson(
        "/api/v1/auth/password-reset/verify",
        objectMapper
            .createObjectNode()
            .put("contactValue", email)
            .put("code", code)
            .put("newPassword", "Another-Horse-2!"));
  }

  private MvcResult postJson(String path, ObjectNode body) throws Exception {
    return mockMvc
        .perform(
            post(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andReturn();
  }

  private JsonNode body(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  /** Status plus the body without request-specific fields, so two responses can be compared. */
  private String comparable(MvcResult result) throws Exception {
    ObjectNode node = (ObjectNode) body(result);
    node.remove(List.of("timestamp", "instance", "traceId", "correlationId"));
    return result.getResponse().getStatus() + " " + node;
  }

  private java.util.Optional<VerificationCode> storedCode(String email) {
    return verificationCodeRepository.findTopByTenantIdAndContactValueAndTypeOrderByCreatedAtDesc(
        TenantContext.SYSTEM_TENANT_ID, email, VerificationType.PASSWORD_RESET);
  }

  private String sentCode(String email) {
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(verificationService, timeout(2_000))
        .sendVerificationCode(
            eq(email), code.capture(), any(), any(), eq(VerificationType.PASSWORD_RESET));
    return code.getValue();
  }
}
