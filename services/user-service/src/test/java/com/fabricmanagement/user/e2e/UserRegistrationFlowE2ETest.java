package com.fabricmanagement.user.e2e;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for User Registration flows.
 * 
 * Tests complete user registration scenarios:
 * 1. Admin-initiated (Direct Registration)
 * 2. User-initiated (Self Registration)
 * 3. Contact verification
 * 4. Password creation
 * 5. First login
 */
@DisplayName("User Registration Flow E2E Tests")
class UserRegistrationFlowE2ETest extends E2ETestBase {

    @Nested
    @DisplayName("Direct Registration Flow (Admin creates user)")
    class DirectRegistrationFlow {

        @Test
        @DisplayName("Should complete full direct registration flow")
        void shouldCompleteDirectRegistrationFlow() {
            // ==========================================
            // Step 1: Admin creates user (Direct Registration)
            // ==========================================
            Map<String, Object> createUserRequest = new HashMap<>();
            createUserRequest.put("tenantId", TEST_TENANT_ID);
            createUserRequest.put("contactType", "EMAIL");
            createUserRequest.put("contactValue", "newuser@example.com");
            createUserRequest.put("firstName", "Jane");
            createUserRequest.put("lastName", "Smith");
            createUserRequest.put("role", "COMPANY_EMPLOYEE");
            createUserRequest.put("registrationType", "DIRECT_REGISTRATION");

            Response createResponse = given()
                .body(createUserRequest)
            .when()
                .post("/users")
            .then()
                .statusCode(201)
                .body("success", is(true))
                .body("data.firstName", equalTo("Jane"))
                .body("data.lastName", equalTo("Smith"))
                .body("data.status", equalTo("PENDING_VERIFICATION"))
                .body("data.role", equalTo("COMPANY_EMPLOYEE"))
                .extract().response();

            String userId = createResponse.path("data.id");
            assertThat(userId).isNotNull();

            // ==========================================
            // Step 2: User receives verification code (simulated)
            // ==========================================
            String verificationCode = "123456"; // In real scenario, this would be sent via email/SMS

            // ==========================================
            // Step 3: User verifies contact
            // ==========================================
            Map<String, Object> verifyContactRequest = new HashMap<>();
            verifyContactRequest.put("contactValue", "newuser@example.com");
            verifyContactRequest.put("verificationCode", verificationCode);

            given()
                .body(verifyContactRequest)
            .when()
                .post("/users/verify-contact")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("verified"));

            // ==========================================
            // Step 4: User creates password
            // ==========================================
            Map<String, Object> createPasswordRequest = new HashMap<>();
            createPasswordRequest.put("contactValue", "newuser@example.com");
            createPasswordRequest.put("password", TEST_PASSWORD);
            createPasswordRequest.put("confirmPassword", TEST_PASSWORD);

            given()
                .body(createPasswordRequest)
            .when()
                .post("/users/create-password")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.status", equalTo("ACTIVE"))
                .body("message", containsString("activated"));

            // ==========================================
            // Step 5: User logs in
            // ==========================================
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("contactValue", "newuser@example.com");
            loginRequest.put("password", TEST_PASSWORD);

            Response loginResponse = given()
                .body(loginRequest)
            .when()
                .post("/auth/login")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.accessToken", notNullValue())
                .body("data.refreshToken", notNullValue())
                .body("data.user.firstName", equalTo("Jane"))
                .body("data.user.lastName", equalTo("Smith"))
                .body("data.user.role", equalTo("COMPANY_EMPLOYEE"))
                .extract().response();

            String accessToken = loginResponse.path("data.accessToken");
            assertThat(accessToken).isNotNull();

            // ==========================================
            // Step 6: User accesses protected resource
            // ==========================================
            givenAuthenticated(accessToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.firstName", equalTo("Jane"))
                .body("data.lastName", equalTo("Smith"));
        }

        @Test
        @DisplayName("Should reject duplicate email registration")
        void shouldRejectDuplicateEmailRegistration() {
            String duplicateEmail = "duplicate@example.com";

            // First registration
            Map<String, Object> createUserRequest1 = new HashMap<>();
            createUserRequest1.put("tenantId", TEST_TENANT_ID);
            createUserRequest1.put("contactType", "EMAIL");
            createUserRequest1.put("contactValue", duplicateEmail);
            createUserRequest1.put("firstName", "First");
            createUserRequest1.put("lastName", "User");
            createUserRequest1.put("role", "COMPANY_EMPLOYEE");
            createUserRequest1.put("registrationType", "DIRECT_REGISTRATION");

            given()
                .body(createUserRequest1)
            .when()
                .post("/users")
            .then()
                .statusCode(201);

            // Duplicate registration attempt
            Map<String, Object> createUserRequest2 = new HashMap<>();
            createUserRequest2.put("tenantId", TEST_TENANT_ID);
            createUserRequest2.put("contactType", "EMAIL");
            createUserRequest2.put("contactValue", duplicateEmail);
            createUserRequest2.put("firstName", "Second");
            createUserRequest2.put("lastName", "User");
            createUserRequest2.put("role", "COMPANY_EMPLOYEE");
            createUserRequest2.put("registrationType", "DIRECT_REGISTRATION");

            given()
                .body(createUserRequest2)
            .when()
                .post("/users")
            .then()
                .statusCode(409) // Conflict
                .body("success", is(false))
                .body("error.code", equalTo("DUPLICATE_CONTACT"));
        }
    }

    @Nested
    @DisplayName("Self Registration Flow (User registers themselves)")
    class SelfRegistrationFlow {

        @Test
        @DisplayName("Should complete full self-registration flow with admin approval")
        void shouldCompleteSelfRegistrationWithApproval() {
            // ==========================================
            // Step 1: User self-registers (External Partner)
            // ==========================================
            Map<String, Object> selfRegisterRequest = new HashMap<>();
            selfRegisterRequest.put("contactType", "EMAIL");
            selfRegisterRequest.put("contactValue", "partner@external.com");
            selfRegisterRequest.put("firstName", "External");
            selfRegisterRequest.put("lastName", "Partner");
            selfRegisterRequest.put("companyName", "External Corp");
            selfRegisterRequest.put("businessType", "SUPPLIER");
            selfRegisterRequest.put("registrationType", "SELF_REGISTRATION");

            Response selfRegisterResponse = given()
                .body(selfRegisterRequest)
            .when()
                .post("/users/self-register")
            .then()
                .statusCode(202) // Accepted (pending approval)
                .body("success", is(true))
                .body("data.status", equalTo("PENDING_APPROVAL"))
                .body("data.role", equalTo("EXTERNAL_PARTNER"))
                .body("message", containsString("approval"))
                .extract().response();

            String userId = selfRegisterResponse.path("data.id");

            // ==========================================
            // Step 2: Admin approves registration
            // ==========================================
            Map<String, Object> approveRequest = new HashMap<>();
            approveRequest.put("userId", userId);
            approveRequest.put("approved", true);
            approveRequest.put("tenantId", TEST_TENANT_ID); // Admin assigns tenant

            given()
                .body(approveRequest)
            .when()
                .post("/users/approve")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.status", equalTo("PENDING_VERIFICATION"));

            // ==========================================
            // Step 3: User verifies contact
            // ==========================================
            String verificationCode = "123456";

            Map<String, Object> verifyContactRequest = new HashMap<>();
            verifyContactRequest.put("contactValue", "partner@external.com");
            verifyContactRequest.put("verificationCode", verificationCode);

            given()
                .body(verifyContactRequest)
            .when()
                .post("/users/verify-contact")
            .then()
                .statusCode(200)
                .body("success", is(true));

            // ==========================================
            // Step 4: User creates password and becomes active
            // ==========================================
            Map<String, Object> createPasswordRequest = new HashMap<>();
            createPasswordRequest.put("contactValue", "partner@external.com");
            createPasswordRequest.put("password", TEST_PASSWORD);
            createPasswordRequest.put("confirmPassword", TEST_PASSWORD);

            given()
                .body(createPasswordRequest)
            .when()
                .post("/users/create-password")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.status", equalTo("ACTIVE"));

            // ==========================================
            // Step 5: User logs in successfully
            // ==========================================
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("contactValue", "partner@external.com");
            loginRequest.put("password", TEST_PASSWORD);

            given()
                .body(loginRequest)
            .when()
                .post("/auth/login")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.user.role", equalTo("EXTERNAL_PARTNER"));
        }

        @Test
        @DisplayName("Should reject login for unapproved self-registration")
        void shouldRejectLoginForUnapprovedUser() {
            // User self-registers
            Map<String, Object> selfRegisterRequest = new HashMap<>();
            selfRegisterRequest.put("contactType", "EMAIL");
            selfRegisterRequest.put("contactValue", "unapproved@example.com");
            selfRegisterRequest.put("firstName", "Unapproved");
            selfRegisterRequest.put("lastName", "User");
            selfRegisterRequest.put("registrationType", "SELF_REGISTRATION");

            given()
                .body(selfRegisterRequest)
            .when()
                .post("/users/self-register")
            .then()
                .statusCode(202)
                .body("data.status", equalTo("PENDING_APPROVAL"));

            // Try to create password (should fail)
            Map<String, Object> createPasswordRequest = new HashMap<>();
            createPasswordRequest.put("contactValue", "unapproved@example.com");
            createPasswordRequest.put("password", TEST_PASSWORD);

            given()
                .body(createPasswordRequest)
            .when()
                .post("/users/create-password")
            .then()
                .statusCode(403) // Forbidden
                .body("success", is(false))
                .body("error.code", equalTo("PENDING_APPROVAL"));
        }
    }

    @Nested
    @DisplayName("Contact Verification Edge Cases")
    class ContactVerificationEdgeCases {

        @Test
        @DisplayName("Should reject invalid verification code")
        void shouldRejectInvalidVerificationCode() {
            // Create user
            Map<String, Object> createUserRequest = new HashMap<>();
            createUserRequest.put("tenantId", TEST_TENANT_ID);
            createUserRequest.put("contactType", "EMAIL");
            createUserRequest.put("contactValue", "verify@example.com");
            createUserRequest.put("firstName", "Verify");
            createUserRequest.put("lastName", "Test");
            createUserRequest.put("role", "COMPANY_EMPLOYEE");

            given()
                .body(createUserRequest)
            .when()
                .post("/users")
            .then()
                .statusCode(201);

            // Try invalid verification code
            Map<String, Object> verifyContactRequest = new HashMap<>();
            verifyContactRequest.put("contactValue", "verify@example.com");
            verifyContactRequest.put("verificationCode", "INVALID_CODE");

            given()
                .body(verifyContactRequest)
            .when()
                .post("/users/verify-contact")
            .then()
                .statusCode(400) // Bad Request
                .body("success", is(false))
                .body("error.code", equalTo("INVALID_VERIFICATION_CODE"));
        }

        @Test
        @DisplayName("Should reject expired verification code")
        void shouldRejectExpiredVerificationCode() {
            // This would require time manipulation or waiting for expiry
            // Placeholder for implementation with @MockBean Clock
        }
    }
}

