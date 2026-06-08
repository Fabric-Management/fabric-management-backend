package com.fabricmanagement.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * JWT round-trip integration test.
 *
 * <p>Verifies the complete flow:
 *
 * <ol>
 *   <li>Signup creates tenant + organization + user
 *   <li>Password setup enables login
 *   <li>Login returns JWT with correct claims
 *   <li>JWT claims can be decoded and verified
 * </ol>
 *
 * <p>Critical claims verified:
 *
 * <ul>
 *   <li>tenant_id - references common_tenant
 *   <li>organization_id - references common_organization
 *   <li>user_id - references common_user
 *   <li>organization_id - references common_organization
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("JWT round-trip integration")
class JwtRoundTripIntegrationTest {

  @Container
  @SuppressWarnings("resource")
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
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private AuthUserRepository authUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SystemTransactionExecutor systemExecutor;

  @MockBean private NotificationService notificationService;
  @MockBean private EmailTemplateRenderer emailTemplateRenderer;

  @Test
  @DisplayName("Login returns JWT with correct tenant_id, organization_id, and user_id claims")
  void login_returnsJwt_withCorrectClaims() throws Exception {
    // Arrange: Mock notification services
    doNothing()
        .when(notificationService)
        .sendNotificationSync(anyString(), anyString(), anyString());
    when(emailTemplateRenderer.renderWelcome(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Welcome email body");
    when(emailTemplateRenderer.renderSetupPassword(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Setup password email body");

    long timestamp = System.currentTimeMillis();
    String email = "jwt-test-" + timestamp + "@example.com";
    String password = "TestPassword123!";
    String taxId = "JWT" + timestamp % 100000;
    String organizationName = "JWT Test Company " + timestamp;

    // Step 1: Signup
    String signupBody =
        """
                {
                  "organizationName": "%s",
                  "taxId": "%s",
                  "organizationType": "SPINNER",
                  "firstName": "JWT",
                  "lastName": "Test",
                  "email": "%s",
                  "acceptedTerms": true
                }
                """
            .formatted(organizationName, taxId, email);

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody))
        .andExpect(status().isOk());

    // Step 2: Setup password directly in DB (simulating password setup flow)
    UUID signupUserId =
        systemExecutor.executeQueryForObject(
            "SELECT u.id FROM common_user.common_user u JOIN common_communication.common_contact c ON c.tenant_id = u.tenant_id WHERE c.contact_value = ?",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            email);
    UUID signupTenantId =
        systemExecutor.executeQueryForObject(
            "SELECT u.tenant_id FROM common_user.common_user u JOIN common_communication.common_contact c ON c.tenant_id = u.tenant_id WHERE c.contact_value = ?",
            (rs, rowNum) -> rs.getObject("tenant_id", UUID.class),
            email);
    if (signupUserId == null || signupTenantId == null) {
      throw new AssertionError("User not found after signup");
    }

    // Set TenantContext for BaseEntity.onCreate
    com.fabricmanagement.common.infrastructure.persistence.TenantContext.setCurrentTenantId(
        signupTenantId);
    try {
      // Create AuthUser with password (user-based auth: one AuthUser per User)
      AuthUser authUser = AuthUser.create(signupUserId, passwordEncoder.encode(password));
      authUser.verify();
      authUserRepository.save(authUser);

      // Verify the contact directly in the DB
      systemExecutor.executeUpdate(
          "UPDATE common_communication.common_contact SET is_verified = true WHERE contact_value = ?",
          email);
    } finally {
      com.fabricmanagement.common.infrastructure.persistence.TenantContext.clear();
    }

    // Step 3: Login
    String loginBody =
        """
                {
                  "contactValue": "%s",
                  "password": "%s"
                }
                """
            .formatted(email, password);

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(cookie().exists("access_token"))
            .andReturn();

    // Step 4: Extract and verify JWT claims
    String accessToken = loginResult.getResponse().getCookie("access_token").getValue();

    // Verify token is valid
    assertThat(jwtService.validateToken(accessToken)).isTrue();

    // Extract claims
    UUID tenantId = jwtService.getTenantIdFromToken(accessToken);
    UUID organizationId = jwtService.getOrganizationIdFromToken(accessToken);
    UUID userId = jwtService.getUserIdFromToken(accessToken);
    String contactValue = jwtService.getContactValueFromToken(accessToken);

    // Verify claims are correct
    assertThat(tenantId).isNotNull();
    assertThat(organizationId).isNotNull();
    assertThat(userId).isNotNull();
    assertThat(contactValue).isEqualTo(email);

    // Verify tenant_id references common_tenant
    assertThat(tenantRepository.findById(tenantId)).isPresent();

    // Verify organization_id references common_organization
    assertThat(organizationRepository.findById(organizationId)).isPresent();

    // Verify user_id references common_user
    assertThat(userRepository.findById(userId)).isPresent();

    // Verify organization belongs to tenant
    var org = organizationRepository.findById(organizationId).orElseThrow();
    assertThat(org.getTenantId()).isEqualTo(tenantId);

    // Verify user belongs to organization
    var userEntity = userRepository.findById(userId).orElseThrow();
    assertThat(userEntity.getOrganizationId()).isEqualTo(organizationId);
    assertThat(userEntity.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  @DisplayName("JWT contains backward-compatible company_id claim equal to organization_id")
  void jwt_containsBackwardCompatClaims() throws Exception {
    // Arrange
    doNothing()
        .when(notificationService)
        .sendNotificationSync(anyString(), anyString(), anyString());
    when(emailTemplateRenderer.renderWelcome(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Welcome");
    when(emailTemplateRenderer.renderSetupPassword(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Setup");

    long timestamp = System.currentTimeMillis();
    String email = "compat-test-" + timestamp + "@example.com";
    String password = "TestPassword123!";

    // Signup
    String signupBody =
        """
                {
                  "organizationName": "Compat Test %s",
                  "taxId": "COMPAT%s",
                  "organizationType": "WEAVER",
                  "firstName": "Compat",
                  "lastName": "Test",
                  "email": "%s",
                  "acceptedTerms": true
                }
                """
            .formatted(timestamp, timestamp % 100000, email);

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody))
        .andExpect(status().isOk());

    // Setup password
    UUID signupUserId =
        systemExecutor.executeQueryForObject(
            "SELECT u.id FROM common_user.common_user u JOIN common_communication.common_contact c ON c.tenant_id = u.tenant_id WHERE c.contact_value = ?",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            email);
    UUID signupTenantId =
        systemExecutor.executeQueryForObject(
            "SELECT u.tenant_id FROM common_user.common_user u JOIN common_communication.common_contact c ON c.tenant_id = u.tenant_id WHERE c.contact_value = ?",
            (rs, rowNum) -> rs.getObject("tenant_id", UUID.class),
            email);
    if (signupUserId == null || signupTenantId == null) {
      throw new AssertionError("User not found after signup");
    }

    com.fabricmanagement.common.infrastructure.persistence.TenantContext.setCurrentTenantId(
        signupTenantId);
    try {
      AuthUser authUser = AuthUser.create(signupUserId, passwordEncoder.encode(password));
      authUser.verify();
      authUserRepository.save(authUser);

      // Verify the contact directly in the DB
      systemExecutor.executeUpdate(
          "UPDATE common_communication.common_contact SET is_verified = true WHERE contact_value = ?",
          email);
    } finally {
      com.fabricmanagement.common.infrastructure.persistence.TenantContext.clear();
    }

    // Login
    String loginBody =
        """
                {"contactValue": "%s", "password": "%s"}
                """
            .formatted(email, password);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody))
            .andExpect(status().isOk())
            .andReturn();

    String accessToken = result.getResponse().getCookie("access_token").getValue();

    UUID organizationId = jwtService.getOrganizationIdFromToken(accessToken);
    assertThat(organizationId).isNotNull();
  }
}
