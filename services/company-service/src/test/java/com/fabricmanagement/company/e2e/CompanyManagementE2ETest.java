package com.fabricmanagement.company.e2e;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for Company Management flows.
 *
 * Tests complete company CRUD scenarios:
 * 1. Create company
 * 2. Get company
 * 3. Update company
 * 4. List companies
 * 5. Search companies
 * 6. Delete company
 * 7. Status management
 * 8. Settings management
 */
@DisplayName("Company Management E2E Tests")
class CompanyManagementE2ETest extends E2ETestBase {

    @Nested
    @DisplayName("Company CRUD Operations")
    class CompanyCrudOperations {

        @Test
        @DisplayName("Should create company successfully")
        void shouldCreateCompanySuccessfully() {
            Map<String, Object> createRequest = new HashMap<>();
            createRequest.put("name", "New Tech Company");
            createRequest.put("legalName", "New Tech Company LLC");
            createRequest.put("type", "LLC");
            createRequest.put("industry", "TECHNOLOGY");
            createRequest.put("description", "A new technology company");
            createRequest.put("website", "http://newtech.com");

            Response createResponse = given()
                .body(createRequest)
            .when()
                .post("/companies")
            .then()
                .statusCode(201)
                .body("success", is(true))
                .body("data", notNullValue())
                .body("message", containsString("created"))
                .extract().response();

            String companyId = createResponse.path("data");
            assertThat(companyId).isNotNull();
        }

        @Test
        @DisplayName("Should get company by ID successfully")
        void shouldGetCompanyByIdSuccessfully() {
            // First create a company
            String companyId = createTestCompany("Get Test Company", "GET_CO");

            // Get the company
            given()
            .when()
                .get("/companies/" + companyId)
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.id", equalTo(companyId))
                .body("data.name", equalTo("Get Test Company"));
        }

        @Test
        @DisplayName("Should update company successfully")
        void shouldUpdateCompanySuccessfully() {
            // Create company
            String companyId = createTestCompany("Update Test Company", "UPDATE_CO");

            // Update company
            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("legalName", "Updated Legal Name");
            updateRequest.put("description", "Updated description");
            updateRequest.put("website", "http://updated.com");

            given()
                .body(updateRequest)
            .when()
                .put("/companies/" + companyId)
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("updated"));

            // Verify update
            given()
            .when()
                .get("/companies/" + companyId)
            .then()
                .statusCode(200)
                .body("data.legalName", equalTo("Updated Legal Name"))
                .body("data.description", equalTo("Updated description"))
                .body("data.website", equalTo("http://updated.com"));
        }

        @Test
        @DisplayName("Should delete company successfully")
        void shouldDeleteCompanySuccessfully() {
            // Create company
            String companyId = createTestCompany("Delete Test Company", "DELETE_CO");

            // Delete company
            given()
            .when()
                .delete("/companies/" + companyId)
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("deleted"));

            // Verify company is deleted (should return 404 or error)
            given()
            .when()
                .get("/companies/" + companyId)
            .then()
                .statusCode(anyOf(is(404), is(500)));
        }
    }

    @Nested
    @DisplayName("Company Listing and Search")
    class CompanyListingAndSearch {

        @Test
        @DisplayName("Should list all companies")
        void shouldListAllCompanies() {
            // Create multiple companies
            createTestCompany("List Company 1", "LIST1");
            createTestCompany("List Company 2", "LIST2");

            given()
            .when()
                .get("/companies")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", notNullValue())
                .body("data.size()", greaterThanOrEqualTo(2));
        }

        @Test
        @DisplayName("Should search companies by name")
        void shouldSearchCompaniesByName() {
            createTestCompany("SearchTest Company", "SEARCH1");

            given()
                .queryParam("name", "SearchTest")
            .when()
                .get("/companies/search")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.size()", greaterThanOrEqualTo(1));
        }

        @Test
        @DisplayName("Should get companies by status")
        void shouldGetCompaniesByStatus() {
            createTestCompany("Active Company", "ACTIVE");

            given()
            .when()
                .get("/companies/status/ACTIVE")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", notNullValue());
        }
    }

    @Nested
    @DisplayName("Company Status Management")
    class CompanyStatusManagement {

        @Test
        @DisplayName("Should activate company successfully")
        void shouldActivateCompany() {
            String companyId = createTestCompany("Activate Test", "ACTIVATE");

            // Deactivate first
            given()
            .when()
                .post("/companies/" + companyId + "/deactivate")
            .then()
                .statusCode(200);

            // Then activate
            given()
            .when()
                .post("/companies/" + companyId + "/activate")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("activated"));
        }

        @Test
        @DisplayName("Should deactivate company successfully")
        void shouldDeactivateCompany() {
            String companyId = createTestCompany("Deactivate Test", "DEACTIVATE");

            given()
            .when()
                .post("/companies/" + companyId + "/deactivate")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("deactivated"));
        }
    }

    @Nested
    @DisplayName("Company Settings Management")
    class CompanySettingsManagement {

        @Test
        @DisplayName("Should update company settings successfully")
        void shouldUpdateCompanySettings() {
            String companyId = createTestCompany("Settings Test", "SETTINGS");

            Map<String, Object> settingsRequest = new HashMap<>();
            settingsRequest.put("timezone", "UTC");
            settingsRequest.put("language", "en");
            settingsRequest.put("currency", "USD");

            Map<String, Object> customSettings = new HashMap<>();
            customSettings.put("theme", "dark");
            customSettings.put("notifications", true);
            settingsRequest.put("settings", customSettings);

            given()
                .body(settingsRequest)
            .when()
                .put("/companies/" + companyId + "/settings")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("settings updated"));
        }

        @Test
        @DisplayName("Should update company subscription successfully")
        void shouldUpdateCompanySubscription() {
            String companyId = createTestCompany("Subscription Test", "SUB");

            Map<String, Object> subscriptionRequest = new HashMap<>();
            subscriptionRequest.put("plan", "PREMIUM");
            subscriptionRequest.put("maxUsers", 50);
            subscriptionRequest.put("endDate", LocalDateTime.now().plusYears(1).toString());

            given()
                .body(subscriptionRequest)
            .when()
                .put("/companies/" + companyId + "/subscription")
            .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("subscription updated"));
        }
    }

    @Nested
    @DisplayName("Company Validation Tests")
    class CompanyValidationTests {

        @Test
        @DisplayName("Should reject company creation with missing required fields")
        void shouldRejectCompanyWithMissingFields() {
            Map<String, Object> invalidRequest = new HashMap<>();
            invalidRequest.put("name", "Invalid Company");
            // Missing type and industry

            given()
                .body(invalidRequest)
            .when()
                .post("/companies")
            .then()
                .statusCode(anyOf(is(400), is(500))); // Bad request or validation error
        }

        @Test
        @DisplayName("Should reject company update for non-existent company")
        void shouldRejectUpdateForNonExistentCompany() {
            UUID nonExistentId = UUID.randomUUID();

            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("legalName", "Test");
            updateRequest.put("description", "Test");

            given()
                .body(updateRequest)
            .when()
                .put("/companies/" + nonExistentId)
            .then()
                .statusCode(anyOf(is(404), is(500))); // Not found or error
        }

        @Test
        @DisplayName("Should reject get request for non-existent company")
        void shouldRejectGetForNonExistentCompany() {
            UUID nonExistentId = UUID.randomUUID();

            given()
            .when()
                .get("/companies/" + nonExistentId)
            .then()
                .statusCode(anyOf(is(404), is(500))); // Not found or error
        }
    }

    // Helper method to create test companies
    private String createTestCompany(String name, String suffix) {
        Map<String, Object> createRequest = new HashMap<>();
        createRequest.put("name", name);
        createRequest.put("legalName", name + " " + suffix);
        createRequest.put("type", "LLC");
        createRequest.put("industry", "TECHNOLOGY");
        createRequest.put("description", "Test company: " + name);

        Response response = given()
            .body(createRequest)
        .when()
            .post("/companies")
        .then()
            .statusCode(201)
            .extract().response();

        return response.path("data");
    }
}
