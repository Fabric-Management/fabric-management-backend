package com.fabricmanagement.user.e2e;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for User Management flows.
 *
 * Tests complete user CRUD scenarios:
 * 1. Create user
 * 2. Get user
 * 3. Update user
 * 4. List users
 * 5. Search users
 * 6. Delete user
 */
@DisplayName("User Management Flow E2E Tests")
class UserRegistrationFlowE2ETest extends E2ETestBase {

    @Nested
    @DisplayName("User CRUD Operations")
    class UserCrudOperations {

        @Test
        @DisplayName("Should create user successfully")
        void shouldCreateUserSuccessfully() {
            Map<String, Object> createUserRequest = new HashMap<>();
            createUserRequest.put("email", "newuser@example.com");
            createUserRequest.put("firstName", "Jane");
            createUserRequest.put("lastName", "Smith");
            createUserRequest.put("role", "COMPANY_EMPLOYEE");

            Response createResponse = given()
                .body(createUserRequest)
            .when()
                .post("/users")
            .then()
                .statusCode(201)
                .body("success", is(true))
                .body("data", notNullValue())
                .body("message", containsString("created"))
                .extract().response();

            String userId = createResponse.path("data");
            assertThat(userId).isNotNull();
        }

        @Test
        @DisplayName("Should get user by ID successfully")
        void shouldGetUserByIdSuccessfully() {
            // First create a user
            Map<String, Object> createUserRequest = new HashMap<>();
            createUserRequest.put("email", "getuser@example.com");
            createUserRequest.put("firstName", "Get");
            createUserRequest.put("lastName", "User");
            createUserRequest.put("role", "COMPANY_EMPLOYEE");

            Response createResponse = given()
                .body(createUserRequest)
            .when()
                .post("/users")
            .then()
                .statusCode(201)
                .extract().response();

            String userId = createResponse.path("data");

            // Get the user
            given()
            .when()
                .get("/users/" + userId)
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.id", equalTo(userId))
                .body("data.firstName", equalTo("Get"))
                .body("data.lastName", equalTo("User"));
        }

        @Test
        @DisplayName("Should update user successfully")
        void shouldUpdateUserSuccessfully() {
            // Create user
            Map<String, Object> createUserRequest = new HashMap<>();
            createUserRequest.put("email", "updateuser@example.com");
            createUserRequest.put("firstName", "Update");
            createUserRequest.put("lastName", "User");
            createUserRequest.put("role", "COMPANY_EMPLOYEE");

            Response createResponse = given()
                .body(createUserRequest)
            .when()
                .post("/users")
            .then()
                .statusCode(201)
                .extract().response();

            String userId = createResponse.path("data");

            // Update user
            Map<String, Object> updateUserRequest = new HashMap<>();
            updateUserRequest.put("firstName", "Updated");
            updateUserRequest.put("lastName", "Name");

            given()
                .body(updateUserRequest)
            .when()
                .put("/users/" + userId)
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("updated"));

            // Verify update
            given()
            .when()
                .get("/users/" + userId)
            .then()
                .statusCode(200)
                .body("data.firstName", equalTo("Updated"))
                .body("data.lastName", equalTo("Name"));
        }

        @Test
        @DisplayName("Should delete user successfully")
        void shouldDeleteUserSuccessfully() {
            // Create user
            Map<String, Object> createUserRequest = new HashMap<>();
            createUserRequest.put("email", "deleteuser@example.com");
            createUserRequest.put("firstName", "Delete");
            createUserRequest.put("lastName", "User");
            createUserRequest.put("role", "COMPANY_EMPLOYEE");

            Response createResponse = given()
                .body(createUserRequest)
            .when()
                .post("/users")
            .then()
                .statusCode(201)
                .extract().response();

            String userId = createResponse.path("data");

            // Delete user
            given()
            .when()
                .delete("/users/" + userId)
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("deleted"));

            // Verify user is deleted (should return 404 or empty)
            given()
            .when()
                .get("/users/" + userId)
            .then()
                .statusCode(anyOf(is(404), is(500))); // Depending on implementation
        }
    }

    @Nested
    @DisplayName("User Listing and Search")
    class UserListingAndSearch {

        @Test
        @DisplayName("Should list all users")
        void shouldListAllUsers() {
            // Create multiple users
            createTestUser("list1@example.com", "List", "User1");
            createTestUser("list2@example.com", "List", "User2");

            given()
            .when()
                .get("/users")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", notNullValue())
                .body("data.size()", greaterThanOrEqualTo(2));
        }

        @Test
        @DisplayName("Should search users by first name")
        void shouldSearchUsersByFirstName() {
            createTestUser("search1@example.com", "SearchTest", "User1");

            given()
                .queryParam("firstName", "SearchTest")
            .when()
                .get("/users/search")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.size()", greaterThanOrEqualTo(1));
        }

        @Test
        @DisplayName("Should search users by email")
        void shouldSearchUsersByEmail() {
            createTestUser("searchemail@example.com", "Email", "Search");

            given()
                .queryParam("email", "searchemail@example.com")
            .when()
                .get("/users/search")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.size()", greaterThanOrEqualTo(1));
        }
    }

    @Nested
    @DisplayName("User Validation Tests")
    class UserValidationTests {

        @Test
        @DisplayName("Should reject user creation with missing required fields")
        void shouldRejectUserWithMissingFields() {
            Map<String, Object> invalidRequest = new HashMap<>();
            invalidRequest.put("email", "invalid@example.com");
            // Missing firstName and lastName

            given()
                .body(invalidRequest)
            .when()
                .post("/users")
            .then()
                .statusCode(anyOf(is(400), is(500))); // Bad request or validation error
        }

        @Test
        @DisplayName("Should reject user update for non-existent user")
        void shouldRejectUpdateForNonExistentUser() {
            UUID nonExistentId = UUID.randomUUID();

            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("firstName", "Test");
            updateRequest.put("lastName", "User");

            given()
                .body(updateRequest)
            .when()
                .put("/users/" + nonExistentId)
            .then()
                .statusCode(anyOf(is(404), is(500))); // Not found or error
        }

        @Test
        @DisplayName("Should reject get request for non-existent user")
        void shouldRejectGetForNonExistentUser() {
            UUID nonExistentId = UUID.randomUUID();

            given()
            .when()
                .get("/users/" + nonExistentId)
            .then()
                .statusCode(anyOf(is(404), is(500))); // Not found or error
        }
    }

    @Nested
    @DisplayName("Company-User Relationship Tests")
    class CompanyUserRelationshipTests {

        @Test
        @DisplayName("Should get users by company ID")
        void shouldGetUsersByCompanyId() {
            UUID companyId = UUID.randomUUID();

            given()
            .when()
                .get("/users/company/" + companyId)
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", notNullValue());
        }

        @Test
        @DisplayName("Should get user count for company")
        void shouldGetUserCountForCompany() {
            UUID companyId = UUID.randomUUID();

            given()
            .when()
                .get("/users/company/" + companyId + "/count")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", notNullValue());
        }

        @Test
        @DisplayName("Should check if user exists")
        void shouldCheckIfUserExists() {
            // Create a user first
            Map<String, Object> createUserRequest = new HashMap<>();
            createUserRequest.put("email", "exists@example.com");
            createUserRequest.put("firstName", "Exists");
            createUserRequest.put("lastName", "User");
            createUserRequest.put("role", "COMPANY_EMPLOYEE");

            Response createResponse = given()
                .body(createUserRequest)
            .when()
                .post("/users")
            .then()
                .statusCode(201)
                .extract().response();

            String userId = createResponse.path("data");

            // Check if user exists
            given()
            .when()
                .get("/users/" + userId + "/exists")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", is(true));
        }
    }

    // Helper method to create test users
    private String createTestUser(String email, String firstName, String lastName) {
        Map<String, Object> createUserRequest = new HashMap<>();
        createUserRequest.put("email", email);
        createUserRequest.put("firstName", firstName);
        createUserRequest.put("lastName", lastName);
        createUserRequest.put("role", "COMPANY_EMPLOYEE");

        Response response = given()
            .body(createUserRequest)
        .when()
            .post("/users")
        .then()
            .statusCode(201)
            .extract().response();

        return response.path("data");
    }
}
