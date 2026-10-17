package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.app.TenantOnboardingService;
import com.fabricmanagement.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.platform.auth.dto.SignupIntent;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.testsupport.PostgresImage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The playground partner network (PLAYGROUND-PARTIES-1) through the real register-first signup,
 * verified in the database: contacts for every demo customer and supplier, address cards, linked
 * companies with their own contacts and cards, a mix of verified and unverified records, and a
 * replay that adds nothing.
 *
 * <p>Runs on the real application role ({@code fabric_app}, row-level security in force) exactly
 * like {@link SalesQuoteDemoSeedIT}; assertions read through the system {@link JdbcTemplate}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PartnerNetworkDemoSeedIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("fabric_test")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    try (Connection conn =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'app_test'");
      stmt.execute(
          "CREATE ROLE fabric_system LOGIN NOSUPERUSER NOCREATEDB BYPASSRLS PASSWORD 'system_test'");
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create test database roles", e);
    }
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "fabric_app");
    registry.add("spring.datasource.password", () -> "app_test");
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("application.system-datasource.username", () -> "fabric_system");
    registry.add("application.system-datasource.password", () -> "system_test");
  }

  @Autowired private TenantOnboardingService onboardingService;
  @Autowired private PartnerNetworkDemoSeeder partnerNetworkDemoSeeder;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private NotificationService notificationService;
  @MockitoBean private EmailTemplateRenderer emailTemplateRenderer;

  @BeforeEach
  void stubMail() {
    when(emailTemplateRenderer.renderWelcome(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("welcome");
    when(emailTemplateRenderer.renderSetupPassword(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn("setup");
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  private static final String OWNER_NAME = "COALESCE(p.custom_name, r.official_name)";
  private static final String PARTNER_JOIN =
      " JOIN common_company.trading_partner_registry r ON r.id = p.registry_id";

  @Test
  void playgroundSignupGivesCustomersAndSuppliersTheirPeopleCardsAndLinkedCompanies() {
    UUID tenant = signupPlayground();

    // People: every demo customer has a buyer; nobody is reachable outside a reserved domain.
    Map<String, List<String>> contacts = contactsByOwner(tenant);
    assertThat(contacts.get("Albion Apparel Ltd"))
        .containsExactlyInAnyOrder("BUYER", "FINANCE", "LOGISTICS");
    assertThat(contacts.get("Anadolu Tekstil A.Ş."))
        .containsExactlyInAnyOrder("BUYER", "FINANCE", "LOGISTICS");
    assertThat(contacts.get("Europa Mode GmbH")).containsExactlyInAnyOrder("BUYER", "QUALITY");
    assertThat(contacts.get("Global Fashion Wear Corp."))
        .containsExactlyInAnyOrder("BUYER", "FINANCE");
    for (String linked :
        List.of(
            "Midlands Garment Makers Ltd",
            "Northern Dyeworks Ltd",
            "Channel Freight Ltd",
            "Çorlu Konfeksiyon Sanayi",
            "Porto Confecções Lda",
            "Dhaka Apparel Works Ltd")) {
      assertThat(contacts.get(linked)).as(linked).hasSize(1);
    }
    for (String supplier : List.of("Ege Pamuk Kooperatifi", "Yarn Source Ltd.", "Dye & Chem BV")) {
      assertThat(contacts.get(supplier)).as(supplier).containsExactly("OTHER");
    }
    assertThat(
            jdbc.queryForList(
                "SELECT email FROM common_company.partner_contact"
                    + " WHERE tenant_id = ? AND email NOT LIKE '%.example'",
                String.class, tenant))
        .isEmpty();

    // Cards: two per customer, one per linked company, the mix of verified and awaiting.
    Map<String, List<String>> cards = cardsByOwner(tenant);
    for (String customer :
        List.of(
            "Albion Apparel Ltd",
            "Anadolu Tekstil A.Ş.",
            "Europa Mode GmbH",
            "Global Fashion Wear Corp.")) {
      assertThat(cards.get(customer)).as(customer).hasSize(2);
    }
    assertThat(cards.get("Northern Dyeworks Ltd")).containsExactly("Dyehouse, Bradford|UNVERIFIED");
    assertThat(cards.get("Channel Freight Ltd")).containsExactly("Dover depot|VERIFIED");
    assertThat(cards.get("Anadolu Tekstil A.Ş."))
        .containsExactlyInAnyOrder("Merkez ofis, Bağcılar|VERIFIED", "Depo, Esenyurt|UNVERIFIED");
    assertThat(
            cards.values().stream()
                .flatMap(List::stream)
                .filter(card -> card.endsWith("|UNVERIFIED"))
                .count())
        .isEqualTo(2);

    // Linked companies: three for Albion, the forwarder shared with Global Fashion as one record.
    Map<String, List<String>> links = linksByCustomer(tenant);
    assertThat(links.get("Albion Apparel Ltd"))
        .containsExactlyInAnyOrder(
            "Midlands Garment Makers Ltd|GARMENT_MAKER|VERIFIED",
            "Northern Dyeworks Ltd|DYEHOUSE|UNVERIFIED",
            "Channel Freight Ltd|FORWARDER|VERIFIED");
    assertThat(links.get("Global Fashion Wear Corp."))
        .containsExactlyInAnyOrder(
            "Dhaka Apparel Works Ltd|GARMENT_MAKER|VERIFIED",
            "Channel Freight Ltd|FORWARDER|VERIFIED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(DISTINCT linked_partner_id) FROM common_company.partner_link"
                    + " pl JOIN common_company.common_trading_partner p ON p.id = pl.linked_partner_id"
                    + PARTNER_JOIN
                    + " WHERE pl.tenant_id = ? AND "
                    + OWNER_NAME
                    + " = 'Channel Freight Ltd'",
                Integer.class,
                tenant))
        .isEqualTo(1);
    assertThat(links.get("Anadolu Tekstil A.Ş.")).hasSize(3);
    assertThat(links.get("Europa Mode GmbH")).hasSize(2);
  }

  @Test
  void replayingTheSeedChangesNothing() {
    UUID tenant = signupPlayground();
    List<Integer> before = counts(tenant);

    TenantContext.executeInTenantContext(tenant, () -> partnerNetworkDemoSeeder.seedFor(tenant));

    assertThat(counts(tenant)).isEqualTo(before);
    assertThat(before).allSatisfy(count -> assertThat(count).isPositive());
  }

  /** Contacts, address cards of partners, links: what a replay must leave as it found it. */
  private List<Integer> counts(UUID tenant) {
    return List.of(
        jdbc.queryForObject(
            "SELECT count(*) FROM common_company.partner_contact WHERE tenant_id = ?",
            Integer.class,
            tenant),
        jdbc.queryForObject(
            "SELECT count(*) FROM common_company.common_organization_address oa"
                + " JOIN common_company.common_trading_partner p"
                + " ON p.organization_id = oa.organization_id WHERE oa.tenant_id = ?",
            Integer.class,
            tenant),
        jdbc.queryForObject(
            "SELECT count(*) FROM common_company.partner_link WHERE tenant_id = ?",
            Integer.class,
            tenant),
        jdbc.queryForObject(
            "SELECT count(*) FROM common_company.common_trading_partner WHERE tenant_id = ?",
            Integer.class,
            tenant));
  }

  private Map<String, List<String>> contactsByOwner(UUID tenant) {
    return group(
        jdbc.queryForList(
            "SELECT "
                + OWNER_NAME
                + " AS owner, pc.role AS item FROM common_company.partner_contact pc"
                + " JOIN common_company.common_trading_partner p ON p.id = pc.partner_id"
                + PARTNER_JOIN
                + " WHERE pc.tenant_id = ? AND pc.is_active",
            tenant));
  }

  private Map<String, List<String>> cardsByOwner(UUID tenant) {
    return group(
        jdbc.queryForList(
            "SELECT "
                + OWNER_NAME
                + " AS owner, a.label || '|' || a.verification_status AS item"
                + " FROM common_company.common_organization_address oa"
                + " JOIN common_communication.common_address a ON a.id = oa.address_id"
                + " JOIN common_company.common_trading_partner p"
                + " ON p.organization_id = oa.organization_id"
                + PARTNER_JOIN
                + " WHERE oa.tenant_id = ? AND oa.is_active",
            tenant));
  }

  private Map<String, List<String>> linksByCustomer(UUID tenant) {
    return group(
        jdbc.queryForList(
            "SELECT COALESCE(c.custom_name, cr.official_name) AS owner,"
                + " COALESCE(l.custom_name, lr.official_name) || '|' || pl.role || '|'"
                + " || l.verification_status AS item"
                + " FROM common_company.partner_link pl"
                + " JOIN common_company.common_trading_partner c ON c.id = pl.customer_partner_id"
                + " JOIN common_company.trading_partner_registry cr ON cr.id = c.registry_id"
                + " JOIN common_company.common_trading_partner l ON l.id = pl.linked_partner_id"
                + " JOIN common_company.trading_partner_registry lr ON lr.id = l.registry_id"
                + " WHERE pl.tenant_id = ? AND pl.is_active",
            tenant));
  }

  private static Map<String, List<String>> group(List<Map<String, Object>> rows) {
    Map<String, List<String>> grouped = new HashMap<>();
    for (Map<String, Object> row : rows) {
      grouped
          .computeIfAbsent((String) row.get("owner"), owner -> new ArrayList<>())
          .add((String) row.get("item"));
    }
    return grouped;
  }

  private UUID signupPlayground() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return onboardingService
        .createSelfServiceTenant(
            SelfSignupRequest.builder()
                .organizationName("Partner Playground " + suffix)
                .taxId("PPG" + suffix)
                .organizationType(OrganizationType.SPINNER)
                .firstName("Demo")
                .lastName("Prospect")
                .email("partner-playground-" + suffix + "@example.com")
                .intent(SignupIntent.PLAYGROUND)
                .acceptedTerms(true)
                .build())
        .getTenantId();
  }
}
