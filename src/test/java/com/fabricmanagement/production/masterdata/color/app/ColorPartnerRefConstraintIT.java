package com.fabricmanagement.production.masterdata.color.app;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ColorPartnerRefConstraintIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  private UUID tenantA;
  private UUID tenantB;
  private UUID colorA1;
  private UUID colorA2;
  private UUID colorB;

  @BeforeEach
  void setUp() throws Exception {
    tenantA = UUID.randomUUID();
    tenantB = UUID.randomUUID();
    colorA1 = insertColor(tenantA, "A1");
    colorA2 = insertColor(tenantA, "A2");
    colorB = insertColor(tenantB, "B1");
  }

  @Test
  void databasePinsTenantIdentityNormalizationPrimaryAndReusableActiveKeys() throws Exception {
    UUID partner = UUID.randomUUID();

    assertThatThrownBy(() -> insertRef(tenantA, colorB, partner, "CUSTOMER"))
        .hasMessageContaining("fk_color_partner_ref_color");

    UUID ref1 = insertRef(tenantA, colorA1, partner, "CUSTOMER");
    UUID ref2 = insertRef(tenantA, colorA2, partner, "CUSTOMER");
    UUID primary =
        insertCode(tenantA, ref1, partner, "CUSTOMER", "Display-1", "DISPLAY-1", true, true);

    assertThatThrownBy(
            () ->
                insertCode(
                    tenantA, ref1, partner, "CUSTOMER", "Display-2", "WRONG-KEY", false, true))
        .hasMessageContaining("chk_color_partner_code_key");

    assertThatThrownBy(
            () ->
                insertCode(
                    tenantA, ref1, UUID.randomUUID(), "CUSTOMER", "DRIFT", "DRIFT", false, true))
        .hasMessageContaining("fk_color_partner_code_ref");

    assertThatThrownBy(
            () -> insertCode(tenantA, ref1, partner, "CUSTOMER", "SECOND", "SECOND", true, true))
        .hasMessageContaining("uq_color_partner_code_active_primary");

    assertThatThrownBy(
            () ->
                insertCode(
                    tenantA, ref2, partner, "CUSTOMER", "display-1", "DISPLAY-1", true, true))
        .hasMessageContaining("uq_color_partner_code_active_lookup");

    update(
        "UPDATE production.color_partner_code SET is_active = false, is_primary = false WHERE id = ?",
        primary);
    assertThatCode(
            () ->
                insertCode(
                    tenantA, ref2, partner, "CUSTOMER", "display-1", "DISPLAY-1", true, true))
        .doesNotThrowAnyException();
  }

  @Test
  void cascadeDeactivateReleasesAKeyAndDemoteFlushPromoteWorksWithThePartialIndex()
      throws Exception {
    UUID switchPartner = UUID.randomUUID();
    UUID switchRef = insertRef(tenantA, colorA1, switchPartner, "SUPPLIER");
    UUID oldPrimary =
        insertCode(tenantA, switchRef, switchPartner, "SUPPLIER", "OLD", "OLD", true, true);
    UUID target =
        insertCode(tenantA, switchRef, switchPartner, "SUPPLIER", "NEW", "NEW", false, true);

    try (Connection connection = connection()) {
      connection.setAutoCommit(false);
      try (PreparedStatement demote =
              connection.prepareStatement(
                  "UPDATE production.color_partner_code SET is_primary = false WHERE id = ?");
          PreparedStatement promote =
              connection.prepareStatement(
                  "UPDATE production.color_partner_code SET is_primary = true WHERE id = ?")) {
        demote.setObject(1, oldPrimary);
        demote.executeUpdate();
        promote.setObject(1, target);
        promote.executeUpdate();
        connection.commit();
      }
    }

    UUID reusablePartner = UUID.randomUUID();
    UUID sourceRef = insertRef(tenantA, colorA1, reusablePartner, "CUSTOMER");
    insertCode(tenantA, sourceRef, reusablePartner, "CUSTOMER", "REUSE", "REUSE", true, true);
    update(
        "UPDATE production.color_partner_code SET is_active = false, is_primary = false "
            + "WHERE color_partner_ref_id = ?",
        sourceRef);
    update("UPDATE production.color_partner_ref SET is_active = false WHERE id = ?", sourceRef);

    UUID destinationRef = insertRef(tenantA, colorA2, reusablePartner, "CUSTOMER");
    assertThatCode(
            () ->
                insertCode(
                    tenantA,
                    destinationRef,
                    reusablePartner,
                    "CUSTOMER",
                    "REUSE",
                    "REUSE",
                    true,
                    true))
        .doesNotThrowAnyException();
  }

  private UUID insertColor(UUID tenantId, String suffix) throws Exception {
    UUID id = UUID.randomUUID();
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO production.color "
                    + "(id, tenant_id, created_at, updated_at, is_active, version, code, name, "
                    + "color_type, color_family, standard_status) "
                    + "VALUES (?, ?, now(), now(), true, 0, ?, ?, 'DYED', 'BLUE', 'DRAFT')")) {
      statement.setObject(1, id);
      statement.setObject(2, tenantId);
      statement.setString(
          3, "CONSTRAINT-" + suffix + "-" + id.toString().substring(0, 8).toUpperCase());
      statement.setString(4, "Constraint " + suffix);
      statement.executeUpdate();
    }
    return id;
  }

  private UUID insertRef(UUID tenantId, UUID colorId, UUID partnerId, String role)
      throws Exception {
    UUID id = UUID.randomUUID();
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO production.color_partner_ref "
                    + "(id, tenant_id, created_at, updated_at, is_active, version, color_id, partner_id, role) "
                    + "VALUES (?, ?, now(), now(), true, 0, ?, ?, ?)")) {
      statement.setObject(1, id);
      statement.setObject(2, tenantId);
      statement.setObject(3, colorId);
      statement.setObject(4, partnerId);
      statement.setString(5, role);
      statement.executeUpdate();
    }
    return id;
  }

  private UUID insertCode(
      UUID tenantId,
      UUID refId,
      UUID partnerId,
      String role,
      String display,
      String key,
      boolean primary,
      boolean active)
      throws Exception {
    UUID id = UUID.randomUUID();
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO production.color_partner_code "
                    + "(id, tenant_id, created_at, updated_at, is_active, version, color_partner_ref_id, "
                    + "partner_id, role, external_code, external_code_key, is_primary) "
                    + "VALUES (?, ?, now(), now(), ?, 0, ?, ?, ?, ?, ?, ?)")) {
      statement.setObject(1, id);
      statement.setObject(2, tenantId);
      statement.setBoolean(3, active);
      statement.setObject(4, refId);
      statement.setObject(5, partnerId);
      statement.setString(6, role);
      statement.setString(7, display);
      statement.setString(8, key);
      statement.setBoolean(9, primary);
      statement.executeUpdate();
    }
    return id;
  }

  private void update(String sql, UUID id) throws Exception {
    try (Connection connection = connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, id);
      statement.executeUpdate();
    }
  }

  private Connection connection() throws Exception {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
