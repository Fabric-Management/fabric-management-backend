package com.fabricmanagement.common.platform.auth.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.common.platform.communication.app.NotificationService;
import com.fabricmanagement.common.platform.organization.domain.Organization;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.common.platform.tenant.domain.Tenant;
import com.fabricmanagement.common.platform.tenant.infra.repository.TenantRepository;
import java.util.Optional;
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
import org.springframework.test.web.servlet.MvcResult;
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
  @Autowired private TenantRepository tenantRepository;
  @Autowired private OrganizationRepository organizationRepository;

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

    String taxId = "111222333" + System.currentTimeMillis() % 10000;
    String body =
        """
        {
          "companyName": "E2E Test Company",
          "taxId": "%s",
          "companyType": "SPINNER",
          "firstName": "Test",
          "lastName": "User",
          "email": "e2e-test-%s@example.com",
          "acceptedTerms": true
        }
        """
            .formatted(taxId, System.currentTimeMillis());

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

  @Test
  @DisplayName("Onboarding creates Tenant in common_tenant table")
  void onboarding_createsTenant_inCommonTenantTable() throws Exception {
    doNothing()
        .when(notificationService)
        .sendNotificationSync(anyString(), anyString(), anyString());
    when(emailTemplateRenderer.renderWelcome(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Welcome email body");
    when(emailTemplateRenderer.renderSetupPassword(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Setup password email body");

    long timestamp = System.currentTimeMillis();
    String companyName = "Tenant Test Company " + timestamp;
    String taxId = "TENANT" + timestamp % 100000;
    String body =
        """
        {
          "companyName": "%s",
          "taxId": "%s",
          "companyType": "VERTICAL_MILL",
          "firstName": "Tenant",
          "lastName": "Test",
          "email": "tenant-test-%s@example.com",
          "acceptedTerms": true
        }
        """
            .formatted(companyName, taxId, timestamp);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/public/signup")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();

    // Verify Tenant was created in common_tenant table
    Optional<Tenant> tenant =
        tenantRepository.findAll().stream()
            .filter(t -> t.getName().equals(companyName))
            .findFirst();

    assertThat(tenant).isPresent();
    assertThat(tenant.get().getName()).isEqualTo(companyName);
    assertThat(tenant.get().getStatus()).isNotNull();
    assertThat(tenant.get().getIsActive()).isTrue();
  }

  @Test
  @DisplayName("Onboarding creates Organization in common_organization table")
  void onboarding_createsOrganization_inCommonOrganizationTable() throws Exception {
    doNothing()
        .when(notificationService)
        .sendNotificationSync(anyString(), anyString(), anyString());
    when(emailTemplateRenderer.renderWelcome(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Welcome email body");
    when(emailTemplateRenderer.renderSetupPassword(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Setup password email body");

    long timestamp = System.currentTimeMillis();
    String companyName = "Org Test Company " + timestamp;
    String taxId = "ORG" + timestamp % 100000;
    String body =
        """
        {
          "companyName": "%s",
          "taxId": "%s",
          "companyType": "WEAVER",
          "firstName": "Org",
          "lastName": "Test",
          "email": "org-test-%s@example.com",
          "acceptedTerms": true
        }
        """
            .formatted(companyName, taxId, timestamp);

    mockMvc
        .perform(
            post("/api/public/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    // Verify Organization was created in common_organization table
    Optional<Organization> org = organizationRepository.findByTaxId(taxId);

    assertThat(org).isPresent();
    assertThat(org.get().getName()).isEqualTo(companyName);
    assertThat(org.get().getTaxId()).isEqualTo(taxId);
    assertThat(org.get().getOrganizationType().name()).isEqualTo("WEAVER");
    assertThat(org.get().getIsActive()).isTrue();

    // Verify tenant_id FK is set correctly
    assertThat(org.get().getTenantId()).isNotNull();

    // Verify Tenant exists for this Organization
    Optional<Tenant> tenant = tenantRepository.findById(org.get().getTenantId());
    assertThat(tenant).isPresent();
    assertThat(tenant.get().getName()).isEqualTo(companyName);
  }
}
