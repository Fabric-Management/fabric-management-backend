package com.fabricmanagement.user.e2e;

import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for Authentication flows.
 * 
 * Tests:
 * 1. Login with email
 * 2. Login with phone
 * 3. Multi-contact login
 * 4. JWT token validation
 * 5. Token refresh
 * 6. Session management
 */
@DisplayName("Authentication Flow E2E Tests")
class AuthenticationFlowE2ETest extends E2ETestBase {

    private String activeUserEmail = "active.user@example.com";
    private String activeUserPhone = "+905559876543";
    private String userId;

    @BeforeEach
    void createActiveUser() {
        // Create and activate a user for login tests
        Map<String, Object> createUserRequest = new HashMap<>();
        createUserRequest.put("tenantId", TEST_TENANT_ID);
        createUserRequest.put("contactType", "EMAIL");
        createUserRequest.put("contactValue", activeUserEmail);
        createUserRequest.put("firstName", "Active");
        createUserRequest.put("lastName", "User");
        createUserRequest.put("role", "COMPANY_EMPLOYEE");
        createUserRequest.put("registrationType", "DIRECT_REGISTRATION");

        Response createResponse = given()
            .body(createUserRequest)
        .when()
            .post("/users")
        .then()
            .statusCode(201)
            .extract().response();

        userId = createResponse.path("data.id");

        // Verify contact
        Map<String, Object> verifyRequest = new HashMap<>();
        verifyRequest.put("contactValue", activeUserEmail);
        verifyRequest.put("verificationCode", "123456");

        given()
            .body(verifyRequest)
        .when()
            .post("/users/verify-contact")
        .then()
            .statusCode(200);

        // Create password
        Map<String, Object> passwordRequest = new HashMap<>();
        passwordRequest.put("contactValue", activeUserEmail);
        passwordRequest.put("password", TEST_PASSWORD);
        passwordRequest.put("confirmPassword", TEST_PASSWORD);

        given()
            .body(passwordRequest)
        .when()
            .post("/users/create-password")
        .then()
            .statusCode(200);
    }

    @Nested
    @DisplayName("Login with Email")
    class EmailLogin {

        @Test
        @DisplayName("Should login successfully with email")
        void shouldLoginWithEmail() {
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("contactValue", activeUserEmail);
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
                .body("data.tokenType", equalTo("Bearer"))
                .body("data.expiresIn", greaterThan(0))
                .body("data.user.id", equalTo(userId))
                .body("data.user.firstName", equalTo("Active"))
                .body("data.user.lastName", equalTo("User"))
                .body("data.user.role", equalTo("COMPANY_EMPLOYEE"))
                .extract().response();

            String accessToken = loginResponse.path("data.accessToken");
            assertThat(accessToken).isNotBlank();
        }

        @Test
        @DisplayName("Should reject invalid password")
        void shouldRejectInvalidPassword() {
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("contactValue", activeUserEmail);
            loginRequest.put("password", "WrongPassword123!");

            given()
                .body(loginRequest)
            .when()
                .post("/auth/login")
            .then()
                .statusCode(401) // Unauthorized
                .body("success", is(false))
                .body("error.code", equalTo("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("Should reject login for non-existent user")
        void shouldRejectNonExistentUser() {
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("contactValue", "nonexistent@example.com");
            loginRequest.put("password", TEST_PASSWORD);

            given()
                .body(loginRequest)
            .when()
                .post("/auth/login")
            .then()
                .statusCode(401)
                .body("success", is(false))
                .body("error.code", equalTo("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("Should reject login for inactive user")
        void shouldRejectInactiveUser() {
            // Create user but don't activate
            Map<String, Object> createUserRequest = new HashMap<>();
            createUserRequest.put("tenantId", TEST_TENANT_ID);
            createUserRequest.put("contactType", "EMAIL");
            createUserRequest.put("contactValue", "inactive@example.com");
            createUserRequest.put("firstName", "Inactive");
            createUserRequest.put("lastName", "User");
            createUserRequest.put("role", "COMPANY_EMPLOYEE");

            given()
                .body(createUserRequest)
            .when()
                .post("/users")
            .then()
                .statusCode(201);

            // Try to login
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("contactValue", "inactive@example.com");
            loginRequest.put("password", TEST_PASSWORD);

            given()
                .body(loginRequest)
            .when()
                .post("/auth/login")
            .then()
                .statusCode(403) // Forbidden
                .body("success", is(false))
                .body("error.code", equalTo("ACCOUNT_NOT_ACTIVATED"));
        }
    }

    /**
     * TODO: Multi-Contact Login Tests
     * 
     * These tests need to be updated to use ContactServiceClient mock
     * Contact management is now handled by Contact Service
     * 
     * Required updates:
     * 1. Mock ContactServiceClient
     * 2. Use WireMock or MockWebServer for Contact Service API
     * 3. Update test scenarios to reflect new architecture
     */
    
    /*
    @Nested
    @DisplayName("Multi-Contact Login")
    class MultiContactLogin {
        // Tests temporarily disabled - needs ContactService integration
    }
    */

    @Nested
    @DisplayName("JWT Token Management")
    class TokenManagement {

        @Test
        @DisplayName("Should access protected resource with valid token")
        void shouldAccessProtectedResource() {
            String accessToken = loginAndGetToken(activeUserEmail, TEST_PASSWORD);

            givenAuthenticated(accessToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.firstName", equalTo("Active"));
        }

        @Test
        @DisplayName("Should reject access with invalid token")
        void shouldRejectInvalidToken() {
            given()
                .header("Authorization", "Bearer INVALID_TOKEN")
            .when()
                .get("/users/profile")
            .then()
                .statusCode(401)
                .body("success", is(false))
                .body("error.code", equalTo("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("Should reject access without token")
        void shouldRejectWithoutToken() {
            given()
            .when()
                .get("/users/profile")
            .then()
                .statusCode(401)
                .body("success", is(false))
                .body("error.code", equalTo("MISSING_TOKEN"));
        }

        @Test
        @DisplayName("Should refresh access token")
        void shouldRefreshAccessToken() {
            // Login
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("contactValue", activeUserEmail);
            loginRequest.put("password", TEST_PASSWORD);

            Response loginResponse = given()
                .body(loginRequest)
            .when()
                .post("/auth/login")
            .then()
                .statusCode(200)
                .extract().response();

            String refreshToken = loginResponse.path("data.refreshToken");

            // Refresh token
            Map<String, Object> refreshRequest = new HashMap<>();
            refreshRequest.put("refreshToken", refreshToken);

            given()
                .body(refreshRequest)
            .when()
                .post("/auth/refresh")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.accessToken", notNullValue())
                .body("data.refreshToken", notNullValue());
        }

        @Test
        @DisplayName("Should reject expired refresh token")
        void shouldRejectExpiredRefreshToken() {
            Map<String, Object> refreshRequest = new HashMap<>();
            refreshRequest.put("refreshToken", "EXPIRED_REFRESH_TOKEN");

            given()
                .body(refreshRequest)
            .when()
                .post("/auth/refresh")
            .then()
                .statusCode(401)
                .body("success", is(false))
                .body("error.code", equalTo("INVALID_REFRESH_TOKEN"));
        }
    }

    @Nested
    @DisplayName("Logout and Session Management")
    class LogoutAndSession {

        @Test
        @DisplayName("Should logout successfully")
        void shouldLogoutSuccessfully() {
            String accessToken = loginAndGetToken(activeUserEmail, TEST_PASSWORD);

            givenAuthenticated(accessToken)
            .when()
                .post("/auth/logout")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("logout"));

            // Token should be invalidated
            givenAuthenticated(accessToken)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(401)
                .body("error.code", equalTo("TOKEN_REVOKED"));
        }

        @Test
        @DisplayName("Should logout from all devices")
        void shouldLogoutFromAllDevices() {
            // Login from multiple "devices" (get multiple tokens)
            String token1 = loginAndGetToken(activeUserEmail, TEST_PASSWORD);
            String token2 = loginAndGetToken(activeUserEmail, TEST_PASSWORD);

            // Verify both tokens work
            givenAuthenticated(token1)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200);

            givenAuthenticated(token2)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(200);

            // Logout from all devices
            givenAuthenticated(token1)
            .when()
                .post("/auth/logout-all")
            .then()
                .statusCode(200);

            // Both tokens should be invalidated
            givenAuthenticated(token1)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(401);

            givenAuthenticated(token2)
            .when()
                .get("/users/profile")
            .then()
                .statusCode(401);
        }
    }

    // Helper method to login and extract token
    private String loginAndGetToken(String contactValue, String password) {
        Map<String, Object> loginRequest = new HashMap<>();
        loginRequest.put("contactValue", contactValue);
        loginRequest.put("password", password);

        Response response = given()
            .body(loginRequest)
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
            .extract().response();

        return response.path("data.accessToken");
    }
}

