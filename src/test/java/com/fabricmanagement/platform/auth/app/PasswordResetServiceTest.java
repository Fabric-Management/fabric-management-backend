package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.MfaType;
import com.fabricmanagement.platform.auth.domain.VerificationType;
import com.fabricmanagement.platform.auth.dto.PasswordResetRequest;
import com.fabricmanagement.platform.auth.dto.PasswordResetVerifyRequest;
import com.fabricmanagement.platform.auth.infra.repository.LoginIdentityRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  @Mock private LoginIdentityRepository loginIdentityRepository;
  @Mock private VerificationCodeManager verificationCodeManager;
  @Mock private IdentityProvisioningService identityProvisioningService;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private PasswordResetService passwordResetService;

  // ── Request step ────────────────────────────────────────────────────────────────────────────

  @Test
  void requestForAnExistingAccountSendsACodeAndAnswersWithTheGenericMessage() {
    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity("admin@example.com", "hash")));

    String message = passwordResetService.requestPasswordReset(request(" Admin@Example.com "));

    assertThat(message).isEqualTo(PasswordResetService.REQUEST_ACCEPTED_MESSAGE);
    verify(verificationCodeManager).issueCode("admin@example.com", VerificationType.PASSWORD_RESET);
  }

  @Test
  void requestForAMissingAccountAnswersExactlyLikeAnExistingOne() {
    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity("admin@example.com", "hash")));
    when(loginIdentityRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

    String existing = passwordResetService.requestPasswordReset(request("admin@example.com"));
    String missing = passwordResetService.requestPasswordReset(request("nobody@example.com"));

    assertThat(missing).isEqualTo(existing);
    verify(verificationCodeManager, never())
        .issueCode("nobody@example.com", VerificationType.PASSWORD_RESET);
  }

  @Test
  void requestForAMissingAccountStillSpendsAHashComputation() {
    when(loginIdentityRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

    passwordResetService.requestPasswordReset(request("nobody@example.com"));

    verify(passwordEncoder).encode(anyString());
  }

  @Test
  void requestDoesNotSendACodeToAnUnverifiedOrInactiveAccount() {
    LoginIdentity unverified = identity("unverified@example.com", "hash");
    unverified.setEmailVerified(false);
    LoginIdentity inactive = identity("inactive@example.com", "hash");
    inactive.setIsActive(false);
    when(loginIdentityRepository.findByEmail("unverified@example.com"))
        .thenReturn(Optional.of(unverified));
    when(loginIdentityRepository.findByEmail("inactive@example.com"))
        .thenReturn(Optional.of(inactive));

    assertThat(passwordResetService.requestPasswordReset(request("unverified@example.com")))
        .isEqualTo(PasswordResetService.REQUEST_ACCEPTED_MESSAGE);
    assertThat(passwordResetService.requestPasswordReset(request("inactive@example.com")))
        .isEqualTo(PasswordResetService.REQUEST_ACCEPTED_MESSAGE);
    verifyNoInteractions(verificationCodeManager);
  }

  @Test
  void aThrottledRequestStillAnswersWithTheGenericMessage() {
    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity("admin@example.com", "hash")));
    when(verificationCodeManager.issueCode("admin@example.com", VerificationType.PASSWORD_RESET))
        .thenThrow(new PlatformDomainException("Too many codes", "AUTH_THROTTLE_CONTACT", 429));

    String message = passwordResetService.requestPasswordReset(request("admin@example.com"));

    assertThat(message).isEqualTo(PasswordResetService.REQUEST_ACCEPTED_MESSAGE);
  }

  // ── Reset step ──────────────────────────────────────────────────────────────────────────────

  @Test
  void resetWithAValidCodeSetsTheNewPassword() {
    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity("admin@example.com", "old-hash")));
    when(passwordEncoder.matches("NewPassword1!", "old-hash")).thenReturn(false);
    when(passwordEncoder.encode("NewPassword1!")).thenReturn("new-hash");

    passwordResetService.resetPassword(
        resetRequest("admin@example.com", "123456", "NewPassword1!"));

    verify(verificationCodeManager)
        .validateAndConsume("admin@example.com", VerificationType.PASSWORD_RESET, "123456");
    verify(identityProvisioningService).updatePassword("admin@example.com", "new-hash");
  }

  @Test
  void missingAccountAndWrongCodeGiveTheSameResponse() {
    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity("admin@example.com", "hash")));
    when(loginIdentityRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
    doThrow(
            new PlatformDomainException(
                "Verification code is invalid or expired", "AUTH_VERIFICATION_CODE_INVALID", 400))
        .when(verificationCodeManager)
        .validateAndConsume("admin@example.com", VerificationType.PASSWORD_RESET, "000000");

    PlatformDomainException wrongCode =
        resetFailure(resetRequest("admin@example.com", "000000", "NewPassword1!"));
    PlatformDomainException missingAccount =
        resetFailure(resetRequest("nobody@example.com", "000000", "NewPassword1!"));

    assertSameExternalResponse(missingAccount, wrongCode);
    assertThat(missingAccount.getErrorCode()).isEqualTo("AUTH_VERIFICATION_CODE_INVALID");
    assertThat(missingAccount.getHttpStatus()).isEqualTo(400);
    verify(identityProvisioningService, never()).updatePassword(anyString(), any());
  }

  @Test
  void missingAccountOnResetStillSpendsAHashComparison() {
    when(loginIdentityRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(anyString())).thenReturn("placeholder-hash");

    resetFailure(resetRequest("nobody@example.com", "000000", "NewPassword1!"));

    verify(passwordEncoder).matches("000000", "placeholder-hash");
  }

  @Test
  void expiredUsedAndExhaustedCodesAllGiveTheWrongCodeResponse() {
    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity("admin@example.com", "hash")));
    when(loginIdentityRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
    PlatformDomainException reference =
        resetFailure(resetRequest("nobody@example.com", "111111", "NewPassword1!"));

    for (PlatformDomainException cause :
        new PlatformDomainException[] {
          new PlatformDomainException("Verification code has expired. Please request a new code."),
          new PlatformDomainException(
              "Verification code has already been used.", "AUTH_VERIFICATION_CODE_USED", 400),
          new PlatformDomainException("Too many verification attempts. Please request a new code.")
        }) {
      doThrow(cause)
          .when(verificationCodeManager)
          .validateAndConsume("admin@example.com", VerificationType.PASSWORD_RESET, "111111");

      assertSameExternalResponse(
          resetFailure(resetRequest("admin@example.com", "111111", "NewPassword1!")), reference);
    }
  }

  @Test
  void reusingTheCurrentPasswordIsRejectedWithItsOwnCode() {
    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity("admin@example.com", "hash")));
    when(passwordEncoder.matches("SamePassword1!", "hash")).thenReturn(true);

    PlatformDomainException failure =
        resetFailure(resetRequest("admin@example.com", "123456", "SamePassword1!"));

    assertThat(failure.getErrorCode()).isEqualTo("AUTH_PASSWORD_REUSE");
    verify(identityProvisioningService, never()).updatePassword(anyString(), any());
  }

  private PlatformDomainException resetFailure(PasswordResetVerifyRequest request) {
    Throwable thrown = catchThrowable(() -> passwordResetService.resetPassword(request));
    assertThat(thrown).isInstanceOf(PlatformDomainException.class);
    return (PlatformDomainException) thrown;
  }

  private static void assertSameExternalResponse(
      PlatformDomainException actual, PlatformDomainException expected) {
    assertThat(actual.getErrorCode()).isEqualTo(expected.getErrorCode());
    assertThat(actual.getHttpStatus()).isEqualTo(expected.getHttpStatus());
    assertThat(actual.getMessage()).isEqualTo(expected.getMessage());
    assertThat(actual.getArgs()).isEqualTo(expected.getArgs());
  }

  private static PasswordResetRequest request(String email) {
    return PasswordResetRequest.builder().contactValue(email).build();
  }

  private static PasswordResetVerifyRequest resetRequest(
      String email, String code, String password) {
    return PasswordResetVerifyRequest.builder()
        .contactValue(email)
        .code(code)
        .newPassword(password)
        .build();
  }

  private static LoginIdentity identity(String email, String passwordHash) {
    return LoginIdentity.builder()
        .id(UUID.randomUUID())
        .email(email)
        .passwordHash(passwordHash)
        .isMfaEnabled(false)
        .primaryMfaType(MfaType.NONE)
        .isActive(true)
        .emailVerified(true)
        .failedLoginAttempts(0)
        .requiresPasswordReset(false)
        .build();
  }
}
