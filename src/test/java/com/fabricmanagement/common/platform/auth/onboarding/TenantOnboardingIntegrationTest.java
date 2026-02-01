package com.fabricmanagement.common.platform.auth.onboarding;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.common.platform.communication.app.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end integration test for tenant onboarding (self-service signup). Uses Testcontainers
 * PostgreSQL; mocks notification/email so no real email is sent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
@DisplayName("Tenant onboarding integration")
class TenantOnboardingIntegrationTest {

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired private MockMvc mockMvc;

  @MockBean private NotificationService notificationService;
  @MockBean private EmailTemplateRenderer emailTemplateRenderer;

  @Test
  @DisplayName("POST /api/public/signup creates tenant and returns success (e2e)")
  void selfServiceSignup_e2e_returnsSuccess() throws Exception {
    doNothing()
        .when(notificationService)
        .sendNotificationSync(anyString(), anyString(), anyString());
    when(emailTemplateRenderer.renderWelcome(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Welcome email body");
    when(emailTemplateRenderer.renderSetupPassword(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Setup password email body");

    String body =
            """
        {
          "companyName": "E2E Test Company",
          "taxId": "1112223334",
          "companyType": "SPINNER",
          "firstName": "Test",
          "lastName": "User",
          "email": "e2e-test-%s@example.com",
          "acceptedTerms": true
        }
        """
            .formatted(System.currentTimeMillis());

    mockMvc
        .perform(
            post("/api/public/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isString())
        .andExpect(jsonPath("$.data").value(org.hamcrest.CoreMatchers.containsString("email")));
  }
}
