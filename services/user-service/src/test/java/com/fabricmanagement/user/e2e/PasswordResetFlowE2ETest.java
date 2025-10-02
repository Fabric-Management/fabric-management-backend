package com.fabricmanagement.user.e2e;

import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for Password Reset flows.
 * 
 * Tests:
 * 1. Request password reset (email/SMS)
 * 2. Verify reset code/link
 * 3. Set new password
 * 4. Login with new password
 * 5. Security constraints (expiry, attempts, reuse)
 */
@DisplayName("Password Reset Flow E2E Tests")
class PasswordResetFlowE2ETest extends E2ETestBase {

    private String userEmail = "resetuser@example.com";
    private String userPhone = "+905551234567";
    private String oldPassword = "OldPassword123!";

    @BeforeEach
    void createActiveUserWithPassword() {
        // Create and activate user
        Map<String, Object> createUserRequest = new HashMap<>();
        createUserRequest.put("tenantId", TEST_TENANT_ID);
        createUserRequest.put("contactType", "EMAIL");
        createUserRequest.put("contactValue", userEmail);
        createUserRequest.put("firstName", "Reset");
        createUserRequest.put("lastName", "User");
        createUserRequest.put("role", "COMPANY_EMPLOYEE");

        given()
            .body(createUserRequest)
        .when()
            .post("/users")
        .then()
            .statusCode(201);

        // Verify contact
        Map<String, Object> verifyRequest = new HashMap<>();
        verifyRequest.put("contactValue", userEmail);
        verifyRequest.put("verificationCode", "123456");

        given()
            .body(verifyRequest)
        .when()
            .post("/users/verify-contact")
        .then()
            .statusCode(200);

        // Create password
        Map<String, Object> passwordRequest = new HashMap<>();
        passwordRequest.put("contactValue", userEmail);
        passwordRequest.put("password", oldPassword);
        passwordRequest.put("confirmPassword", oldPassword);

        given()
            .body(passwordRequest)
        .when()
            .post("/users/create-password")
        .then()
            .statusCode(200);
    }

    @Nested
    @DisplayName("Complete Password Reset Flow")
    class CompletePasswordResetFlow {

        @Test
        @DisplayName("Should complete password reset via email link")
        void shouldResetPasswordViaEmailLink() {
            // ==========================================
            // Step 1: Request password reset
            // ==========================================
            Map<String, Object> resetRequest = new HashMap<>();
            resetRequest.put("contactValue", userEmail);
            resetRequest.put("resetMethod", "EMAIL");

            Response resetResponse = given()
                .body(resetRequest)
            .when()
                .post("/auth/password-reset/request")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("reset link sent"))
                .body("data.resetMethod", equalTo("EMAIL"))
                .body("data.expiresIn", equalTo(900)) // 15 minutes = 900 seconds
                .extract().response();

            String resetToken = resetResponse.path("data.resetToken");
            assertThat(resetToken).isNotNull();

            // ==========================================
            // Step 2: Verify reset token
            // ==========================================
            Map<String, Object> verifyTokenRequest = new HashMap<>();
            verifyTokenRequest.put("contactValue", userEmail);
            verifyTokenRequest.put("resetToken", resetToken);

            given()
                .body(verifyTokenRequest)
            .when()
                .post("/auth/password-reset/verify")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.valid", is(true))
                .body("data.remainingAttempts", equalTo(3));

            // ==========================================
            // Step 3: Set new password
            // ==========================================
            String newPassword = "NewSecurePass456!";
            Map<String, Object> resetPasswordRequest = new HashMap<>();
            resetPasswordRequest.put("contactValue", userEmail);
            resetPasswordRequest.put("resetToken", resetToken);
            resetPasswordRequest.put("newPassword", newPassword);
            resetPasswordRequest.put("confirmPassword", newPassword);

            given()
                .body(resetPasswordRequest)
            .when()
                .post("/auth/password-reset/complete")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("password reset successful"));

            // ==========================================
            // Step 4: Old password should not work
            // ==========================================
            Map<String, Object> loginWithOldPassword = new HashMap<>();
            loginWithOldPassword.put("contactValue", userEmail);
            loginWithOldPassword.put("password", oldPassword);

            given()
                .body(loginWithOldPassword)
            .when()
                .post("/auth/login")
            .then()
                .statusCode(401)
                .body("error.code", equalTo("INVALID_CREDENTIALS"));

            // ==========================================
            // Step 5: New password should work
            // ==========================================
            Map<String, Object> loginWithNewPassword = new HashMap<>();
            loginWithNewPassword.put("contactValue", userEmail);
            loginWithNewPassword.put("password", newPassword);

            given()
                .body(loginWithNewPassword)
            .when()
                .post("/auth/login")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.accessToken", notNullValue());
        }

        @Test
        @DisplayName("Should reset password via SMS code")
        void shouldResetPasswordViaSMSCode() {
            // Request reset via SMS (using phone number)
            Map<String, Object> resetRequest = new HashMap<>();
            resetRequest.put("contactValue", userPhone);
            resetRequest.put("resetMethod", "SMS");

            Response resetResponse = given()
                .body(resetRequest)
            .when()
                .post("/auth/password-reset/request")
            .then()
                .statusCode(200)
                .body("data.resetMethod", equalTo("SMS"))
                .extract().response();

            String resetCode = resetResponse.path("data.resetCode");
            assertThat(resetCode).hasSize(6); // 6-digit code

            // Verify code
            Map<String, Object> verifyCodeRequest = new HashMap<>();
            verifyCodeRequest.put("contactValue", userPhone);
            verifyCodeRequest.put("resetCode", resetCode);

            given()
                .body(verifyCodeRequest)
            .when()
                .post("/auth/password-reset/verify")
            .then()
                .statusCode(200)
                .body("data.valid", is(true));

            // Reset password
            String newPassword = "SMSResetPass789!";
            Map<String, Object> resetPasswordRequest = new HashMap<>();
            resetPasswordRequest.put("contactValue", userPhone);
            resetPasswordRequest.put("resetCode", resetCode);
            resetPasswordRequest.put("newPassword", newPassword);
            resetPasswordRequest.put("confirmPassword", newPassword);

            given()
                .body(resetPasswordRequest)
            .when()
                .post("/auth/password-reset/complete")
            .then()
                .statusCode(200)
                .body("success", is(true));

            // Login with new password
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("contactValue", userEmail);
            loginRequest.put("password", newPassword);

            given()
                .body(loginRequest)
            .when()
                .post("/auth/login")
            .then()
                .statusCode(200);
        }
    }

    @Nested
    @DisplayName("Security Constraints")
    class SecurityConstraints {

        @Test
        @DisplayName("Should reject expired reset token")
        void shouldRejectExpiredToken() {
            // Simulate expiry - use an invalid/expired token
            Map<String, Object> verifyExpiredRequest = new HashMap<>();
            verifyExpiredRequest.put("contactValue", userEmail);
            verifyExpiredRequest.put("resetToken", "EXPIRED_TOKEN_123456");

            given()
                .body(verifyExpiredRequest)
            .when()
                .post("/auth/password-reset/verify")
            .then()
                .statusCode(400)
                .body("success", is(false))
                .body("error.code", equalTo("RESET_TOKEN_EXPIRED"));
        }

        @Test
        @DisplayName("Should block after 3 failed verification attempts")
        void shouldBlockAfterThreeFailedAttempts() {
            // Request reset
            Map<String, Object> resetRequest = new HashMap<>();
            resetRequest.put("contactValue", userEmail);

            given()
                .body(resetRequest)
            .when()
                .post("/auth/password-reset/request")
            .then()
                .statusCode(200);

            // Attempt 1 - wrong code
            Map<String, Object> wrongAttempt = new HashMap<>();
            wrongAttempt.put("contactValue", userEmail);
            wrongAttempt.put("resetCode", "WRONG1");

            given()
                .body(wrongAttempt)
            .when()
                .post("/auth/password-reset/verify")
            .then()
                .statusCode(400)
                .body("error.code", equalTo("INVALID_RESET_CODE"))
                .body("data.remainingAttempts", equalTo(2));

            // Attempt 2 - wrong code
            wrongAttempt.put("resetCode", "WRONG2");
            given()
                .body(wrongAttempt)
            .when()
                .post("/auth/password-reset/verify")
            .then()
                .statusCode(400)
                .body("data.remainingAttempts", equalTo(1));

            // Attempt 3 - wrong code (final attempt)
            wrongAttempt.put("resetCode", "WRONG3");
            given()
                .body(wrongAttempt)
            .when()
                .post("/auth/password-reset/verify")
            .then()
                .statusCode(429) // Too Many Requests
                .body("success", is(false))
                .body("error.code", equalTo("MAX_RESET_ATTEMPTS_EXCEEDED"))
                .body("data.remainingAttempts", equalTo(0));

            // Further attempts should be blocked
            wrongAttempt.put("resetCode", "CORRECT_CODE");
            given()
                .body(wrongAttempt)
            .when()
                .post("/auth/password-reset/verify")
            .then()
                .statusCode(429)
                .body("error.code", equalTo("RESET_TOKEN_LOCKED"));
        }

        @Test
        @DisplayName("Should invalidate token after successful use")
        void shouldInvalidateTokenAfterUse() {
            // Request reset
            Map<String, Object> resetRequest = new HashMap<>();
            resetRequest.put("contactValue", userEmail);

            Response resetResponse = given()
                .body(resetRequest)
            .when()
                .post("/auth/password-reset/request")
            .then()
                .statusCode(200)
                .extract().response();

            String resetToken = resetResponse.path("data.resetToken");

            // Reset password successfully
            String newPassword = "NewPassword123!";
            Map<String, Object> resetPasswordRequest = new HashMap<>();
            resetPasswordRequest.put("contactValue", userEmail);
            resetPasswordRequest.put("resetToken", resetToken);
            resetPasswordRequest.put("newPassword", newPassword);
            resetPasswordRequest.put("confirmPassword", newPassword);

            given()
                .body(resetPasswordRequest)
            .when()
                .post("/auth/password-reset/complete")
            .then()
                .statusCode(200);

            // Try to use the same token again
            Map<String, Object> reuseAttempt = new HashMap<>();
            reuseAttempt.put("contactValue", userEmail);
            reuseAttempt.put("resetToken", resetToken);
            reuseAttempt.put("newPassword", "AnotherPassword456!");
            reuseAttempt.put("confirmPassword", "AnotherPassword456!");

            given()
                .body(reuseAttempt)
            .when()
                .post("/auth/password-reset/complete")
            .then()
                .statusCode(400)
                .body("error.code", equalTo("RESET_TOKEN_ALREADY_USED"));
        }

        @Test
        @DisplayName("Should allow password reuse (no password history check)")
        void shouldAllowPasswordReuse() {
            // Reset to new password
            Map<String, Object> resetRequest = new HashMap<>();
            resetRequest.put("contactValue", userEmail);

            Response resetResponse = given()
                .body(resetRequest)
            .when()
                .post("/auth/password-reset/request")
            .then()
                .statusCode(200)
                .extract().response();

            String resetToken = resetResponse.path("data.resetToken");

            // Verify token
            Map<String, Object> verifyRequest = new HashMap<>();
            verifyRequest.put("contactValue", userEmail);
            verifyRequest.put("resetToken", resetToken);

            given()
                .body(verifyRequest)
            .when()
                .post("/auth/password-reset/verify")
            .then()
                .statusCode(200);

            // Set password to OLD password (should be allowed)
            Map<String, Object> resetToOldPassword = new HashMap<>();
            resetToOldPassword.put("contactValue", userEmail);
            resetToOldPassword.put("resetToken", resetToken);
            resetToOldPassword.put("newPassword", oldPassword);
            resetToOldPassword.put("confirmPassword", oldPassword);

            given()
                .body(resetToOldPassword)
            .when()
                .post("/auth/password-reset/complete")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", not(containsString("already used")));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Validations")
    class EdgeCasesAndValidations {

        @Test
        @DisplayName("Should validate password strength")
        void shouldValidatePasswordStrength() {
            // Request reset
            Map<String, Object> resetRequest = new HashMap<>();
            resetRequest.put("contactValue", userEmail);

            Response resetResponse = given()
                .body(resetRequest)
            .when()
                .post("/auth/password-reset/request")
            .then()
                .statusCode(200)
                .extract().response();

            String resetToken = resetResponse.path("data.resetToken");

            // Try weak password
            Map<String, Object> weakPasswordRequest = new HashMap<>();
            weakPasswordRequest.put("contactValue", userEmail);
            weakPasswordRequest.put("resetToken", resetToken);
            weakPasswordRequest.put("newPassword", "weak");
            weakPasswordRequest.put("confirmPassword", "weak");

            given()
                .body(weakPasswordRequest)
            .when()
                .post("/auth/password-reset/complete")
            .then()
                .statusCode(400)
                .body("success", is(false))
                .body("error.code", equalTo("WEAK_PASSWORD"))
                .body("error.details", hasItem(containsString("minimum")));
        }

        @Test
        @DisplayName("Should reject password mismatch")
        void shouldRejectPasswordMismatch() {
            Map<String, Object> resetRequest = new HashMap<>();
            resetRequest.put("contactValue", userEmail);

            Response resetResponse = given()
                .body(resetRequest)
            .when()
                .post("/auth/password-reset/request")
            .then()
                .statusCode(200)
                .extract().response();

            String resetToken = resetResponse.path("data.resetToken");

            Map<String, Object> mismatchRequest = new HashMap<>();
            mismatchRequest.put("contactValue", userEmail);
            mismatchRequest.put("resetToken", resetToken);
            mismatchRequest.put("newPassword", "Password123!");
            mismatchRequest.put("confirmPassword", "DifferentPassword456!");

            given()
                .body(mismatchRequest)
            .when()
                .post("/auth/password-reset/complete")
            .then()
                .statusCode(400)
                .body("error.code", equalTo("PASSWORD_MISMATCH"));
        }

        @Test
        @DisplayName("Should reject reset for non-existent user")
        void shouldRejectNonExistentUser() {
            Map<String, Object> resetRequest = new HashMap<>();
            resetRequest.put("contactValue", "nonexistent@example.com");

            given()
                .body(resetRequest)
            .when()
                .post("/auth/password-reset/request")
            .then()
                .statusCode(200) // Don't reveal user existence
                .body("success", is(true))
                .body("message", containsString("If the email exists"));
        }

        @Test
        @DisplayName("Should handle concurrent reset requests")
        void shouldHandleConcurrentResetRequests() {
            // First reset request
            Map<String, Object> resetRequest1 = new HashMap<>();
            resetRequest1.put("contactValue", userEmail);

            Response response1 = given()
                .body(resetRequest1)
            .when()
                .post("/auth/password-reset/request")
            .then()
                .statusCode(200)
                .extract().response();

            String token1 = response1.path("data.resetToken");

            // Second reset request (should invalidate first)
            Map<String, Object> resetRequest2 = new HashMap<>();
            resetRequest2.put("contactValue", userEmail);

            Response response2 = given()
                .body(resetRequest2)
            .when()
                .post("/auth/password-reset/request")
            .then()
                .statusCode(200)
                .extract().response();

            String token2 = response2.path("data.resetToken");
            assertThat(token1).isNotEqualTo(token2);

            // First token should be invalidated
            Map<String, Object> verifyOldToken = new HashMap<>();
            verifyOldToken.put("contactValue", userEmail);
            verifyOldToken.put("resetToken", token1);

            given()
                .body(verifyOldToken)
            .when()
                .post("/auth/password-reset/verify")
            .then()
                .statusCode(400)
                .body("error.code", equalTo("RESET_TOKEN_SUPERSEDED"));

            // Second token should work
            Map<String, Object> verifyNewToken = new HashMap<>();
            verifyNewToken.put("contactValue", userEmail);
            verifyNewToken.put("resetToken", token2);

            given()
                .body(verifyNewToken)
            .when()
                .post("/auth/password-reset/verify")
            .then()
                .statusCode(200)
                .body("data.valid", is(true));
        }
    }
}

